#!/usr/bin/env bash
W=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/exit
bash $W/run-g1.sh > $W/run-g1.out 2>&1; echo "g1 rc=$?" > $W/G1.done
bash $W/run-g2.sh > $W/run-g2.out 2>&1; echo "g2 rc=$?" > $W/G2.done
