
package org.eduprom.miners.adaptiveNoise.benchmarks;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.deckfour.xes.model.XLog;
import org.eduprom.benchmarks.IBenchmark;
import org.eduprom.benchmarks.configuration.Weights;
import org.eduprom.entities.CrossValidationPartition;
import org.eduprom.exceptions.ExportFailedException;
import org.eduprom.exceptions.LogFileNotFoundException;
import org.eduprom.exceptions.MiningException;
import org.eduprom.exceptions.ParsingException;
import org.eduprom.miners.AbstractMiner;
import org.eduprom.miners.adaptiveNoise.AdaMiner;
import org.eduprom.miners.adaptiveNoise.trunk.AdaptiveNoiseMiner;
import org.eduprom.miners.adaptiveNoise.IntermediateMiners.NoiseInductiveMiner;
import org.eduprom.miners.adaptiveNoise.conformance.ConformanceInfo;
import org.eduprom.utils.LogHelper;
import org.eduprom.utils.PetrinetHelper;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.processtree.ProcessTree;
import org.processmining.ptconversions.pn.ProcessTree2Petrinet;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.processmining.plugins.petrinet.replayresult.PNRepResult.TRACEFITNESS;

public class AdaBenchmarkValidation implements IBenchmark<AdaptiveNoiseMiner, NoiseInductiveMiner> {

    protected static final Logger logger = Logger.getLogger(AbstractMiner.class.getName());
    static final String FITNESS_KEY = TRACEFITNESS;
    private final UUID runId;
    LogHelper logHelper;
    final AdaptiveNoiseBenchmarkConfiguration adaptiveNoiseBenchmarkConfiguration;
    String path;
    int testSize;

    /*
    private Set<String> getTraces(XLog log){
        return log.stream().map(x->x.stream().map(y->y.getAttributes().get("concept:name").toString())
                .collect(Collectors.joining("->"))).collect(Collectors.toSet());
    }*/

    private ConformanceInfo getNewConformanceInfo(Weights weights){
        return new ConformanceInfo(weights.getFitnessWeight(),
                weights.getPrecisionWeight(),
                weights.getGeneralizationWeight());

    }

    public static ConformanceInfo getPsi(PetrinetHelper petrinetHelper, ProcessTree processTree, XLog trainingLog, XLog validationLog, Weights weights) throws MiningException {
        ConformanceInfo info = new ConformanceInfo(weights);
        ProcessTree2Petrinet.PetrinetWithMarkings res = PetrinetHelper.ConvertToPetrinet(processTree);
        PNRepResult alignment = petrinetHelper.getAlignment(trainingLog, res.petrinet, res.initialMarking, res.finalMarking);
        double fitness = Double.parseDouble(alignment.getInfo().get(FITNESS_KEY).toString());
        //this.petrinetHelper.printResults(alignment);
        info.setFitness(fitness);

        double precision = petrinetHelper.getPrecision(trainingLog, res.petrinet, alignment, res.initialMarking, res.finalMarking);
        info.setPrecision(precision);

        PNRepResult genAlignment = petrinetHelper.getAlignment(validationLog, res.petrinet, res.initialMarking, res.finalMarking);
        double generalization = Double.parseDouble(genAlignment.getInfo().get(FITNESS_KEY).toString());
        info.setGeneralization(generalization);


        //AlignmentPrecGenRes alignmentPrecGenRes = petrinetHelper.getConformance(trainingLog, res.petrinet, alignment, res.initialMarking, res.finalMarking);
        //info.setPrecision(alignmentPrecGenRes.getPrecision());
        //info.setGeneralization(alignmentPrecGenRes.getGeneralization());
        return info;
    }

    public AdaBenchmarkValidation(AdaptiveNoiseBenchmarkConfiguration adaptiveNoiseBenchmarkConfiguration, int testSize){
        this.adaptiveNoiseBenchmarkConfiguration = adaptiveNoiseBenchmarkConfiguration;
        this.logHelper = new LogHelper();
        this.testSize = testSize;
        String format = "./Output/%s.csv";
        this.path = String.format(format, this.getName());
        this.runId = UUID.randomUUID();
        //String format = "./Output/%s-%s.csv";
        //this.path = String.format(format, this.getName(),
        //        new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()));
    }

    @Override
    public List<AdaptiveNoiseMiner> getSources(String filename) throws Exception {
        throw new NotImplementedException();

    }

    @Override
    public List<NoiseInductiveMiner> getTargets(String filename) throws LogFileNotFoundException {
        List<NoiseInductiveMiner> benchmarkableMiners = new ArrayList<>();
        for(float noiseThreshold: this.adaptiveNoiseBenchmarkConfiguration.getNoiseThresholds()){
            benchmarkableMiners.add(new NoiseInductiveMiner(filename, noiseThreshold, false));
            benchmarkableMiners.add(new NoiseInductiveMiner(filename, noiseThreshold, true));
        }

        return benchmarkableMiners;
    }

    protected BenchmarkLogs getBenchmarkLogs(String filename) throws ParsingException {
        XLog log = logHelper.read(filename);

        List<CrossValidationPartition> origin =  this.logHelper.crossValidationSplit(log, testSize);
        CrossValidationPartition[] testPartition = CrossValidationPartition.take(origin, 1);
        origin = CrossValidationPartition.exclude(origin, testPartition);
        CrossValidationPartition[] validationPartition = CrossValidationPartition.take(origin, 1);
        origin = CrossValidationPartition.exclude(origin, validationPartition);

        XLog testLog = CrossValidationPartition.bind(testPartition).getLog();
        XLog validationLog = CrossValidationPartition.bind(validationPartition).getLog();
        XLog trainingLog = CrossValidationPartition.bind(origin).getLog();

        return new BenchmarkLogs()
        {{
            setTrainLog(trainingLog);
            setValidationLog(validationLog);
            setTestLog(testLog);
        }};
    }

    private void processAdaptiveNoise(AdaMiner adaMiner, BenchmarkLogs benchmarkLogs, Weights weights) throws MiningException {
        //mine the models
        adaMiner.setLog(benchmarkLogs.getTrainLog());
        adaMiner.setValidationLog(benchmarkLogs.getValidationLog());
        adaMiner.mine();
        adaMiner.setConformanceInfo(getPsi(adaMiner.getHelper(), adaMiner.getProcessTree(),
                benchmarkLogs.getTrainLog(), benchmarkLogs.getTestLog(), weights));
    }



    public void run() throws Exception {

        boolean includePreFiter = false;
        logger.info(String.format("run_id: %s, total executions: %d", this.runId,
                this.adaptiveNoiseBenchmarkConfiguration.getFilenames().size() * this.adaptiveNoiseBenchmarkConfiguration.getWeights().size()));

        int executions = 0;
        for(String filename: adaptiveNoiseBenchmarkConfiguration.getFilenames()){
            executions++;
            logger.info(String.format("run_id: %s, started execution %d/%d", this.runId,
                    executions,
                    this.adaptiveNoiseBenchmarkConfiguration.getFilenames().size() * this.adaptiveNoiseBenchmarkConfiguration.getWeights().size()));


            BenchmarkLogs benchmarkLogs = getBenchmarkLogs(filename);
            logger.info(benchmarkLogs.toString());

            for (Weights weights: adaptiveNoiseBenchmarkConfiguration.getWeights()) {

                AdaMiner adaMinerImi =
                        new AdaMiner(filename, this.adaptiveNoiseBenchmarkConfiguration.getAdaptiveNoiseConfiguration(weights, false));
                processAdaptiveNoise(adaMinerImi, benchmarkLogs, weights);

                List<NoiseInductiveMiner> targets = getTargets(filename);
                List<NoiseInductiveMiner> miners = null;

                AdaMiner adaMinerImiTag = null;
                NoiseInductiveMiner preBestBaseline = null;
                if (includePreFiter) {
                    adaMinerImiTag =
                            new AdaMiner(filename, this.adaptiveNoiseBenchmarkConfiguration.getAdaptiveNoiseConfiguration(weights, true));
                    processAdaptiveNoise(adaMinerImiTag, benchmarkLogs, weights);

                    miners = targets.stream().filter(NoiseInductiveMiner::isFilterPreExecution).collect(Collectors.toList());

                    preBestBaseline = obtainBest(miners, benchmarkLogs.getTrainLog(), benchmarkLogs.getValidationLog(), weights);
                    preBestBaseline.setConformanceInfo(getPsi(preBestBaseline.getHelper(),
                            preBestBaseline.getProcessTree(), benchmarkLogs.getTrainLog(), benchmarkLogs.getTestLog(), weights));
                }


                miners = targets.stream().filter(x-> !x.isFilterPreExecution()).collect(Collectors.toList());
                NoiseInductiveMiner nonPreFilterBestBaseline = obtainBest(miners, benchmarkLogs.getTrainLog(), benchmarkLogs.getValidationLog(), weights);
                nonPreFilterBestBaseline.setConformanceInfo(getPsi(nonPreFilterBestBaseline.getHelper(),
                        nonPreFilterBestBaseline.getProcessTree(), benchmarkLogs.getTrainLog(), benchmarkLogs.getTestLog(), weights));

                logger.log(Level.INFO, String.format("BEST AN MODEL (d=IMi) : %s, %s",
                        adaMinerImi.getConformanceInfo().toString(), adaMinerImi.getProcessTree().toString()));
                logger.log(Level.INFO, String.format("BEST BASELINE (IMi)   : %s, %s, (noise %f )",
                        nonPreFilterBestBaseline.getConformanceInfo().toString(),
                        nonPreFilterBestBaseline.getResult().getProcessTree().toString(), nonPreFilterBestBaseline.getNoiseThreshold()));

                if (includePreFiter){
                    logger.log(Level.INFO, String.format("BEST AN MODEL(d=IMi') : %s, %s",
                            adaMinerImiTag.getConformanceInfo().toString(), adaMinerImiTag.getProcessTree().toString()));
                    logger.log(Level.INFO, String.format("BEST BASELINE(IMi')  : %s, %s, (noise %f )",
                            preBestBaseline.getConformanceInfo().toString(),
                            preBestBaseline.getResult().getProcessTree().toString(),
                            preBestBaseline.getNoiseThreshold()));
                }

                sendResult(adaMinerImi, nonPreFilterBestBaseline, filename, miners);
            }
        }
    }

    private NoiseInductiveMiner obtainBest(List<NoiseInductiveMiner> noiseInductiveMiners, XLog trainLog, XLog validationLog, Weights weights) throws MiningException {
        NoiseInductiveMiner bestBaseline = null;
        for(NoiseInductiveMiner miner: noiseInductiveMiners){
            miner.setLog(trainLog);
            miner.mine();
            miner.setConformanceInfo(getPsi(miner.getHelper(), miner.getProcessTree(), trainLog, validationLog, weights));

            if (bestBaseline == null || bestBaseline.getConformanceInfo().getPsi() <= miner.getConformanceInfo().getPsi()){
                bestBaseline = miner;
            }
        }

        return bestBaseline;
    }

    private void sendResult(AdaMiner source,
                            NoiseInductiveMiner inductiveMiner,
                            String filename,
                            List<NoiseInductiveMiner> miners) throws ExportFailedException {
        try{
            boolean appendSchema = true;
            if (new File(path).isFile()){
                FileReader fileReader = new FileReader(path);
                CSVReader reader = new CSVReader(fileReader);

                if (reader.readAll().size() > 0){
                    appendSchema = false;
                }
                fileReader.close();
                reader.close();
            }


            CSVWriter csvWriter = new CSVWriter(new FileWriter(path, true));
            String[] schema = new String[]
                    {
                            "run_id",
                            "current_time",
                            "group",
                            "filename",
                            "duration",
                            "noise_thresholds",
                            "fitness_weight",
                            "precision_weight",
                            "generalization_weight",
                            "best_psi",
                            "best_fitness",
                            "best_precision",
                            "best_generalization",
                            "baseline_duration",
                            "baseline_noise",
                            "baseline_psi",
                            "baseline_fitness",
                            "baseline_precision",
                            "baseline_generalization"
                    };

            String[] data = new String[] {
                    this.runId.toString(),
                    Instant.now().toString(),
                    filename.contains("2017") ? "BPM-2017" : filename.contains("2016") ? "BPM-2016" : "DFCI",
                    filename.replace("EventLogs\\contest_2017\\log", "BPM-2017-Log")
                            .replace("EventLogs\\contest_dataset\\training_log_","BPM-2016-Log")
                            .replace(".xes", ""),
                    String.valueOf(source.getElapsedMiliseconds() / 1000.0),
                    org.apache.commons.lang3.StringUtils.join(this.adaptiveNoiseBenchmarkConfiguration.getNoiseThresholds(), ',') ,
                    String.valueOf(source.getConformanceInfo().getFitnessWeight()),
                    String.valueOf(source.getConformanceInfo().getPrecisionWeight()),
                    String.valueOf(source.getConformanceInfo().getGeneralizationWeight()),
                    String.valueOf(source.getConformanceInfo().getPsi()),
                    String.valueOf(source.getConformanceInfo().getFitness()),
                    String.valueOf(source.getConformanceInfo().getPrecision()),
                    String.valueOf(source.getConformanceInfo().getGeneralization()),
                    String.valueOf(miners.stream().mapToDouble(x-> x.getElapsedMiliseconds() / 1000.0).sum()),
                    String.valueOf(inductiveMiner.getNoiseThreshold()),
                    String.valueOf(inductiveMiner.getConformanceInfo().getPsi()),
                    String.valueOf(inductiveMiner.getConformanceInfo().getFitness()),
                    String.valueOf(inductiveMiner.getConformanceInfo().getPrecision()),
                    String.valueOf(inductiveMiner.getConformanceInfo().getGeneralization())
            };


            if (appendSchema){
                csvWriter.writeNext(schema);
            }
            csvWriter.writeNext(data);
            csvWriter.close();
        }
        catch (Exception e){
            throw new ExportFailedException(e);
        }
    }

    public String getName(){
        return "AdaptiveNoise";
    }
}
