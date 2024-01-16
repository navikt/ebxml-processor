#!/usr/bin/perl
use strict;
use warnings;
use File::Copy;
use XML::LibXML;
use List::Util qw(any);
use MIME::Parser;
use autodie;

my ($inputDirectory, $newEmottakDirectory, $oldEmottakDirectory) = @ARGV;

if (
    not defined $inputDirectory or
    not defined $newEmottakDirectory or
    not defined $oldEmottakDirectory
) {
    die "Need directory parameters (call with command line arguments 'inputDirectory' 'newEmottakDirectory' 'oldEmottakDirectory'\n";
}

my $dryRunMode = 1;
if ($dryRunMode ne 0) {
    print "OBS! dryRunMode active, will not actually move any files!\n";
}

# Service og action kombinasjoner som skal flyttes
my %messageTypes = (
    'behandlerKrav' => ["BehandlerKrav", "OppgjorsMelding"],
    'testType' => ["testService", "testAction"]
);

printf "Input directory       :  %s\n", $inputDirectory;
printf "New eMottak directory :  %s\n", $newEmottakDirectory;
printf "Old eMottak directory :  %s\n", $oldEmottakDirectory;

opendir(DIR, $inputDirectory) or die "Can't open $inputDirectory: $!";

my $newCounter = 0;
my $oldCounter = 0;
my $fileCounter = 0;

foreach my $filename (readdir(DIR)) {
    if (length($filename) > 2) {
        $fileCounter++;
        my $parser = MIME::Parser->new;
        $parser->output_to_core(1); #ikke skriv fil til disk

        # Leser epost
        my $entity = $parser->parse_open("$inputDirectory/$filename");
        # $entity->dump_skeleton();
        my $first_part = $entity->parts(0);
        my $body = (defined $first_part) ? $first_part->bodyhandle->as_string : $entity->bodyhandle->as_string;

        # Leser ebxml dokument
        my $xml_parser = XML::LibXML->new;
        my $dom = $xml_parser->parse_string($body);

        my $xpc = XML::LibXML::XPathContext->new($dom);
        $xpc->registerNs('soap',  'http://schemas.xmlsoap.org/soap/envelope/');
        $xpc->registerNs('eb', 'http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd');

        # Henter service og action
        my $service = $xpc->findnodes('/soap:Envelope/soap:Header/eb:MessageHeader/eb:Service');
        my $action = $xpc->findnodes('/soap:Envelope/soap:Header/eb:MessageHeader/eb:Action');

        my $messageMatched = 0;
        foreach my $key (keys %messageTypes) {
            my @serviceAction = @{$messageTypes{$key}};
            if ($serviceAction[0] eq $service and $serviceAction[1] eq $action) {
                $messageMatched = 1;
            }
        }

        if ($messageMatched eq 1) {
            if ($dryRunMode eq 0) {
                move("$inputDirectory/$filename", "$newEmottakDirectory/$filename");
            }
            printf "%s sent to new system!\n", $filename;
            $newCounter++;
        }
        else {
            if ($dryRunMode eq 0) {
                move("$inputDirectory/$filename", "$oldEmottakDirectory/$filename");
            }
            printf "%s sent to old system!\n", $filename;
            $oldCounter++;
        }
    }
}

printf "%s of %s files moved to new system\n", $newCounter, $fileCounter;
printf "%s of %s files moved to old system\n", $oldCounter, $fileCounter;

closedir(DIR);
