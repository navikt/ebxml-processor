#!/usr/bin/perl
use strict;
use warnings;
use File::Copy;
use XML::LibXML;
use List::Util qw(any);
use MIME::Parser;

use autodie;

my $readDirectory = "in";
my $writeDirectory = "out";
my $fileTypeFilter = ".msg";

my @behandlerKrav = ("BehandlerKrav", "OppgjorsMelding");
my @testType = ("testService", "testAction");

my $dryRunOnly = 1;

printf "Checking directory %s...\n", $readDirectory;
opendir(DIR, $readDirectory) or die "Can't open $readDirectory: $!";
printf "Moving files to directory %s...\n", $writeDirectory;

my $moveCounter = 0;
my $fileCounter = 0;

foreach my $filename (readdir(DIR)) {
    $fileCounter = $fileCounter + 1;

    if ($filename =~ m/$fileTypeFilter/) {
        my $parser = new MIME::Parser;
        $parser->output_to_core(1); #ikke skriv fil til disk

        # Leser epost
        my $entity = $parser->parse_open("$readDirectory/$filename");
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
            if ($dryRunOnly eq 0) {
                move("$readDirectory/$filename", "$writeDirectory/$filename");
                printf "%s moved!\n", $filename;
            } else {
                printf "%s moved, but not really because dryRunOnly is true\n", $filename;
            }
            $moveCounter = $moveCounter + 1;
        }
    } else {
#        printf "-skipped %s!\n", $filename;
    }
}

printf "%s of %s files moved\n", $moveCounter, $fileCounter;

closedir(DIR);
