package org.eduprom.miners.adaptiveNoise.IntermediateMiners;

import org.eduprom.miners.adaptiveNoise.FilterAlgorithm;
import org.processmining.processtree.ProcessTree;

/**
 * Created by ydahari on 10/5/2017.
 */
public class MiningResult {

    private ProcessTree processTree;
    private FilterAlgorithm.FilterResult filterResult;

    public MiningResult(ProcessTree processTree, FilterAlgorithm.FilterResult filterResult){
        this.processTree = processTree;
        this.filterResult = filterResult;
    }

    public ProcessTree getProcessTree() {
        return processTree;
    }

    public FilterAlgorithm.FilterResult getFilterResult() {
        return filterResult;
    }
}