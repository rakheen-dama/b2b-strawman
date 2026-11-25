#!/usr/bin/perl
# Interim EmptyState metric (architecture §18.1 row "EmptyState calls without variant";
# recipe: inventory .arch-context-phase91.md §4.1). For each tracked non-test
# frontend/**/*.tsx containing "<EmptyState", match /<EmptyState\b(.*?)\/?>/gs and read variant=.
# "shared" = file imports from "@/components/empty-state".
# Run from the repo root: perl qa_cycle/evidence/phase91-ui/baseline/scripts/emptystate-scan.pl
use strict; use warnings;
my @files = grep { !/__tests__|\.test\.tsx$/ } split /\n/, `git ls-files 'frontend/**/*.tsx'`;
my ($tags, $tagFiles, $shared, $sharedFiles, $withV, $withoutShared, $withoutAll) = (0,0,0,0,0,0,0);
my @noVariant;
for my $f (@files) {
  open my $fh, '<', $f or next; local $/; my $src = <$fh>; close $fh;
  next unless $src =~ /<EmptyState\b/;
  my $isShared = $src =~ m{from\s+["']\@/components/empty-state["']} ? 1 : 0;
  my $n = 0;
  while ($src =~ /<EmptyState\b(.*?)\/?>/gs) {
    my $attrs = $1; $n++; $tags++;
    my $line = 1 + (() = substr($src, 0, $-[0]) =~ /\n/g);
    if ($attrs =~ /\bvariant=/) { $withV++ if $isShared; }
    else { $withoutAll++; if ($isShared) { $withoutShared++; push @noVariant, "$f:$line"; } }
    $shared++ if $isShared;
  }
  $tagFiles++ if $n; $sharedFiles++ if $n && $isShared;
}
print "tags=$tags files=$tagFiles shared_tags=$shared shared_files=$sharedFiles shared_with_variant=$withV shared_without_variant=$withoutShared all_without_variant=$withoutAll\n";
if ($ENV{LIST}) { print "  $_\n" for @noVariant; }
