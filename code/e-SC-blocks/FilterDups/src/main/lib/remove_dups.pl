#!/usr/bin/perl -w

# Read FASTA input from STDIN
$seq = "";
while (<>) {
  if (/^>/) {
    $s{uc($seq)} = 1 if $seq ne "";
    $seq = "";
  } else {
    chomp;
    chop if /\r$/;
    $seq .= $_;
  }
}
$s{$seq} = 1 if $seq ne "";

# Generate a new FASTA file removing duplicate sequences
open(OUTPUT, "> coi.fasta") || die;
$co = 1;
foreach (keys %s) {
  print OUTPUT "> CO$co\n";
  @l = unpack("(A80)*");
  foreach (@l) {
    print OUTPUT "$_\n";
  }
  $co++;
}
close(OUTPUT);
