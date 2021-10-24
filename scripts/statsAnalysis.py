#!/usr/bin/env python
# coding: utf-8

# ## Process the results of tests

# In[1]:


import matplotlib

import numpy as np
import os, sys, getopt, math
import pandas as pd
import matplotlib.pyplot as plt
from ipywidgets import interact, interactive, fixed, interact_manual


# In[2]:


class Analysis:
    def __init__(self, folder):
        self.df = []
        self.folder = folder
        try:
            self.read_data()
            self.df = pd.DataFrame(self.df, columns=['threadNum','report','comp','latency(ms)','rate(t/s)','tuples','processPerTuple(microsec)','totalProcess(microsec)'])
            export_csv = self.df.to_csv (self.folder+'/results.csv', header=True)
        
        except Exception as e:
            print('FAILED TO LOAD THE TEXT FILE: ', e)
        
    def read_data(self):
        threads = [1,2,4,8,16,32]
        reports = [20000, 40000, 80000, 160000]
        epsilons = [.3, .5, .7, 1]
        for epsilon in epsilons:
            for thread in threads:
                for report in reports:

                    curFolder = self.folder + "/threads"+str(thread)+"-report"+str(report)+"-eps"+str(epsilon)+"/"

                    path = curFolder+"comp.txt"
                    with open(path) as f:
                        count = 0
                        val = 0;
                        for line in f:
                            val += int(line)
                            count +=1
                        comp = val/count

                    path = curFolder+"latency.txt"
                    with open(path) as f:
                        count = 0
                        val = 0;
                        for line in f:
                            val += int(line)
                            count +=1
                        latency = val/count

                    path = curFolder+"rate.txt"
                    with open(path) as f:
                        count = 0
                        val = 0;
                        for line in f:
                            val += int(line)
                            count +=1
                        rate = val/count

                    path = curFolder+"time.txt"
                    with open(path) as f:
                        count = 0
                        tup = 0;
                        tim = 0
                        for line in f:
                            vals = line.split(',')
                            tup += int(vals[0])
                            tim += int(vals[1])
                            count +=1
                        tup = tup/count
                        tim = tim/count


                    self.df.append(
                    {
                        'threadNum':thread,
                        'report':report,
                        'epsilon':epsilon,
                        'comp':round(comp, 2),
                        'latency(ms)':round(latency, 2), 
                        'rate(t/s)':round(rate, 2), 
                        'tuples':round(tup, 2),
                        'processPerTuple(microsec)':round(tim, 2),
                        'totalProcess(microsec)':round(tup*tim, 2)
                    })
        
    def process(self):        
        self.plotScalability()
        self.plotPerformance()
        
    def plotScalability(self):
        fig, axes = plt.subplots(nrows=1, ncols=1, sharex=True)
        fig.set_size_inches(4, 4)
        
        xVals = sorted(self.df['threadNum'].unique())
        yVals = []
        
        for thread in xVals:
            val = self.df.loc[(self.df['threadNum'] == thread) & (self.df['reportPeriod'] == 160000), 'processingTime(ms)'].iloc[0]
            yVals.append(val/1000)
        
        axes.set_ylabel('Processing Time (sec)')
        axes.set_xlabel('# PT')
        axes.plot(xVals, yVals)
        axes.grid(True, linestyle='dotted')
        axes.tick_params(direction="in")
        
        fig.align_ylabels()
        fig.tight_layout()
        fig.savefig(self.folder+'/scalability.pdf')
        
    def plotPerformance(self):
        fig, axes = plt.subplots(nrows=1, ncols=1, sharex=True)
        fig.set_size_inches(4, 4)
        
        reports = sorted(self.df['reportPeriod'].unique())
        xVals = [report/10000 for report in reports]
        threads = sorted(self.df['threadNum'].unique())
        
        for thread in threads:
            if thread == 1:
                label = "1 thread"
            else:
                label = str(thread) + " threads"
            yVals = []
            for report in reports:
                val = self.df.loc[(self.df['threadNum'] == thread) & (self.df['reportPeriod'] == report), 'processingTime(ms)'].iloc[0]
                yVals.append(val/1000)
            axes.plot(xVals, yVals, label=label)
        
        axes.set_ylabel('Processing Time (sec)')
        axes.set_xlabel('Report Period ($10^4$ tup)')
        axes.grid(True, linestyle='dotted')
        axes.tick_params(direction="in")
        axes.set_xticks(xVals)
        axes.legend()
        
        fig.align_ylabels()
        fig.tight_layout()
        fig.savefig(self.folder+'/performance.pdf')
        


# In[3]:


def process(folder):
    plt.close('all')
    analysis = Analysis(folder)
#     analysis.process()


# # Interactive code
# The following block is only for jupyter-notebook. Comment it out before extracting the python script
# 
# To convert this file to py, use the following command:
# <code> jupyter nbconvert --to script 'statsAnalysis.ipynb' </code>

# In[6]:


# DATA_DIRECTORY='../results'

# def sorted_ls(path):
#     mtime = lambda f: os.stat(os.path.join(path, f)).st_mtime
#     dirs = list(sorted(os.listdir(path), key=mtime, reverse=True))
#     return [directory for directory in dirs if os.path.isdir(os.path.join(path, directory))]
    
# def selectDataDirectory(directory):
#     global DATA_DIRECTORY
#     DATA_DIRECTORY=f'{directory}'
#     interact(selectFolder, folderName=sorted_ls(DATA_DIRECTORY))
    
# def selectFolder(folderName):
#     process(f'{DATA_DIRECTORY}/{folderName}')

    
# interact(selectDataDirectory, directory=DATA_DIRECTORY)


# In[5]:


def main(argv):
    try:
        opts, args = getopt.getopt(argv,"h",["path="])
    except getopt.GetoptError:
        print('statsAnalysis.py --path <path>')
        sys.exit(2)
    
    for opt, arg in opts:
        if opt == '-h':
            print('statsAnalysis.py --path <path>')
            sys.exit()
        elif opt == '--path':
            FOLDER = arg
    
    process(FOLDER)


if __name__ == '__main__':
    main(sys.argv[1:])

