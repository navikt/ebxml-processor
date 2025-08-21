#!/usr/bin/perl
use strict;
use warnings;
use File::Copy;
use XML::LibXML;
use List::Util qw(any);
use MIME::Parser;
use autodie;

my ($inputDirectory, $newEmottakDirectory, $oldEmottakDirectory, $errorDirectory) = @ARGV;

if (
    not defined $inputDirectory or
    not defined $newEmottakDirectory or
    not defined $oldEmottakDirectory or
    not defined $errorDirectory
) {
    print "Missing directory parameters!\n";
    print "Required usage: perl mail_router.pl <in> <new> <old> <error>\n";
    print "Optional usage: Append -commit after directory lists to disable dry run only mode. Dry run is default behaviour.\n";
    print "Optional usage: Append -both after directory lists to send all messages to both new and old.\n";
    print "E.g: perl mail_router.pl <in> <new> <old> <error> -commit -both\n";
    exit(0);
}

my ($dryRunMode, $sendToBothSystems) = (1, 0);
foreach my $argument (4 .. $#ARGV) {
    if($ARGV[$argument] eq "-commit") {
        $dryRunMode = 0;
    }
    if($ARGV[$argument] eq "-both") {
        $sendToBothSystems = 1;
    }
}

my $lockFile = "/tmp/lockfile";
checkLockfile($lockFile);

if ($dryRunMode ne 0) {
    print "OBS! dryRunMode active, will not actually move any files!\n";
}
if ($sendToBothSystems ne 0) {
    print "OBS! sendToBothSystems active, will send all messages to both output folders!\n";
}

# Service og action kombinasjoner som skal flyttes
my %messageTypes = (
    'behandlerKrav' => ["BehandlerKrav", "OppgjorsMelding"],
    'testType' => ["testService", "testAction"]
);

my $starttime = time;
my $localtime = localtime($starttime);
print "\n";
print "Current local time          : $localtime\n";
print "Input directory             : $inputDirectory\n";
print "New eMottak directory       : $newEmottakDirectory\n";
print "Old eMottak directory       : $oldEmottakDirectory\n";
print "Error directory             : $errorDirectory\n\n";

opendir(DIR, $inputDirectory) or die "Can't open $inputDirectory: $!";

my ($newCounter, $oldCounter, $errorCounter, $fileCounter) = (0, 0, 0, 0);

foreach my $filename (readdir(DIR)) {
    if (length($filename) > 2) {
        $fileCounter++;

        my $document = eval {
            extractXmlMessage("$inputDirectory/$filename");
        };
        if ($@ ne '') {
            if ($dryRunMode eq 0) {
                move("$inputDirectory/$filename", "$errorDirectory/$filename");
            }
            printf "Message '%s' failed with error %s", $filename, $@;
            $errorCounter++;
            next;
        };

        my $xpc = XML::LibXML::XPathContext->new($document);
        $xpc->registerNs('soap',  'http://schemas.xmlsoap.org/soap/envelope/');
        $xpc->registerNs('eb', 'http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd');

        # Henter service og action
        my $service = $xpc->findnodes('/soap:Envelope/soap:Header/eb:MessageHeader/eb:Service');
        my $action = $xpc->findnodes('/soap:Envelope/soap:Header/eb:MessageHeader/eb:Action');

        my $messageMatched = 0;
        if ($action eq 'Acknowledgment' or $action eq 'MessageError') {
            my $refToMessageId = $xpc->findnodes('/soap:Envelope/soap:Header/eb:MessageHeader/eb:MessageData/eb:RefToMessageId');
            if ( index($refToMessageId, '.nav.no') == -1 ) {
                $messageMatched = 1;
            }
        }
        else {
            foreach my $key (keys %messageTypes) {
                my @serviceAction = @{$messageTypes{$key}};
                if ($serviceAction[0] eq $service and $serviceAction[1] eq $action) {
                    $messageMatched = 1;
                }
            }
        }

        if ($messageMatched eq 1) {
            if ($dryRunMode eq 0) {
                if ($sendToBothSystems eq 1) {
                    print "sendToBothSystems active, sending copy to old system\n";
                    copy("$inputDirectory/$filename", "$oldEmottakDirectory/$filename");
                    $oldCounter++;
                }
                move("$inputDirectory/$filename", "$newEmottakDirectory/$filename");
            }
            printf "%s sent to new system!\n", $filename;
            $newCounter++;
        }
        else {
            if ($dryRunMode eq 0) {
                if ($sendToBothSystems eq 1) {
                    print "sendToBothSystems active, sending copy to new system\n";
                    copy("$inputDirectory/$filename", "$newEmottakDirectory/$filename");
                    $newCounter++;
                }
                move("$inputDirectory/$filename", "$oldEmottakDirectory/$filename");
            }
            printf "%s sent to old system!\n", $filename;
            $oldCounter++;
        }
    }
}

print "\n";
printf "Messages processed          : %s/%s\n", $newCounter+$oldCounter+$errorCounter, $fileCounter;
printf "Messages sent to new system : %s\n", $newCounter;
printf "Messages sent to old system : %s\n", $oldCounter;
printf "Messages sent to error      : %s\n", $errorCounter;
printf "Time processing messages    : %s ms\n", time - $starttime;

closedir(DIR);
unlink $lockFile;

printf "Removed %s, completed successfully, exiting now\n", $lockFile;
exit(0);


sub checkLockfile {
    my $lfile = $_[0];
    if (-e $lfile) {
        printf "Lockfile exists at %s, exiting...\n", $lfile;
        exit(1);
    } else {
        open "lockfile", '>', $lfile;
        printf "Lockfile created at %s\n", $lfile;
    }
}

sub extractXmlMessage {
    my $filepath = $_[0];
    my $parser = MIME::Parser->new;
    $parser->output_to_core(1); #ikke skriv fil til disk
    # Leser epost
    my $entity = $parser->parse_open($filepath);
    # $entity->dump_skeleton();
    my $first_part = $entity->parts(0);
    my $body = (defined $first_part) ? $first_part->bodyhandle->as_string : $entity->bodyhandle->as_string;
    # Leser ebxml dokument
    my $xml_parser = XML::LibXML->new;
    return $xml_parser->parse_string($body);
}
