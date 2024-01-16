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

my $fileTypeFilter = ".msg";

# Service og action kombinasjoner som skal flyttes
my @behandlerKrav = ("BehandlerKrav", "OppgjorsMelding");
my @testType = ("testService", "testAction");

printf "Checking directory %s...\n", $inputDirectory;
opendir(DIR, $inputDirectory) or die "Can't open $inputDirectory: $!";
printf "Moving files to directory %s...\n", $newEmottakDirectory;

my $moveCounter = 0;
my $fileCounter = 0;

foreach my $filename (readdir(DIR)) {
    if ($filename =~ m/$fileTypeFilter/) {
        $fileCounter++;
        my $parser = MIME::Parser->new;
        $parser->output_to_core(1); #ikke skriv fil til disk

        # Leser epost
        my $entity = $parser->parse_open("$inputDirectory/$filename");
        my $first_part = $entity->parts(0);
        my $body = $first_part->bodyhandle->as_string;

        # Leser ebxml dokument
        my $xml_parser = XML::LibXML->new;
        my $dom = $xml_parser->parse_string($body);

        my $xpc = XML::LibXML::XPathContext->new($dom);
        $xpc->registerNs('soap',  'http://schemas.xmlsoap.org/soap/envelope/');
        $xpc->registerNs('eb', 'http://www.oasis-open.org/committees/ebxml-msg/schema/msg-header-2_0.xsd');

        # Henter service og action
        my $service = $xpc->findnodes('/soap:Envelope/soap:Header/eb:MessageHeader/eb:Service');
        my $action = $xpc->findnodes('/soap:Envelope/soap:Header/eb:MessageHeader/eb:Action');

        if (
            (any { $_ eq $service } @behandlerKrav and any { $_ eq $action } @behandlerKrav)
            or (any { $_ eq $service } @testType and any { $_ eq $action } @testType)
        ) {
            if ($dryRunMode eq 0) {
                move("$inputDirectory/$filename", "$newEmottakDirectory/$filename");
            }
            printf "%s sent to new system!\n", $filename;
            $moveCounter++;
        }
        else {
            if ($dryRunMode eq 0) {
                move("$inputDirectory/$filename", "$oldEmottakDirectory/$filename");
            }
            printf "%s sent to old system!\n", $filename;
            $moveCounter++;
        }
    }
}

printf "%s of %s files of type %s moved\n", $moveCounter, $fileCounter, $fileTypeFilter;

closedir(DIR);
