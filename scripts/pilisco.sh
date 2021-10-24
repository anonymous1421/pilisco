#!/bin/bash

MAIN_CLASS="pilisco.LinearProcessing"

# Commit number plus a unique identifier to keep track of experiments
UNIQUE_ID=$(date +%Y%m%d%H%M)
COMMIT=$(git rev-parse --short HEAD)
COMMIT_HASH="${COMMIT}_${UNIQUE_ID}"

BASE_DIR="$(pwd)/../" #Path for the pom.xml and other folders
JAR="${BASE_DIR}target/pi-lisco-0.0.1-${COMMIT}.jar"

DATA="${BASE_DIR}data/real_data.txt"
CONFIG="${BASE_DIR}data/realDataConfig.txt"
RESULTS_DIR="${BASE_DIR}results/${COMMIT_HASH}_incremental" #Results data path

REPORT=(20000 40000 80000 160000)
THREADS=(1 2 4 8 16 32)
EPSILON=(0.3 0.5 0.7 1)

#Run
title="[*] Running the experiment using commit $COMMIT"
echo "$title"
for eps in "${EPSILON[@]}"; do
  for parallelism in "${THREADS[@]}"; do
      for report in "${REPORT[@]}"; do
          Res_Folder="${RESULTS_DIR}/threads${parallelism}-report${report}-eps${eps}"
          eval "mkdir -p ${Res_Folder}"
          sleep 10

          cmd="java -Xmx15G -Xms15G -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints
          -Djava.library.path=${BASE_DIR} -cp $JAR ${MAIN_CLASS} -e $eps
          -d $DATA -c $CONFIG -o ${Res_Folder} -ns $parallelism -r $report -f"

          echo "$cmd" >> ${RESULTS_DIR}/script.txt
          echo $cmd
          eval $cmd
      done
  done
done

sleep 30
echo "[*] Processing stats..."
cmd="python3 statsAnalysis.py --path ${RESULTS_DIR}"
echo "$cmd" >> ${RESULTS_DIR}/script.txt
echo $cmd
eval $cmd

title="[*] Finished the experiment using commit $COMMIT"
