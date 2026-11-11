#!/usr/bin/env bash
W=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/exit
until [ -f $W/G2.done ]; do sleep 5; done
bash $W/rerun-lifecycle.sh > $W/rerun-lifecycle.out 2>&1; echo "L rc=$?" > $W/L.done
