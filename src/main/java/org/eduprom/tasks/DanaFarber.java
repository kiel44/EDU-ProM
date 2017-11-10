package org.eduprom.tasks;

import org.eduprom.benchmarks.Weights;
import org.eduprom.miners.adaptiveNoise.benchmarks.AdaptiveNoiseBenchmarkConfiguration;
import org.eduprom.miners.adaptiveNoise.benchmarks.AdaptiveNoiseBenchmarkDfci;
import org.eduprom.benchmarks.IBenchmark;
import org.eduprom.miners.adaptiveNoise.configuration.AdaptiveNoiseConfiguration;
import org.eduprom.partitioning.InductiveCutSplitting;
import org.eduprom.partitioning.trunk.InductiveLogSplitting;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


public class DanaFarber {
	
	private static final LogManager logManager = LogManager.getLogManager();
	private static final Logger logger = Logger.getLogger(DanaFarber.class.getName());
	
    public static void main(String[] args) throws Exception {

		String trainFile = "EventLogs\\\\DFCI_Train_April.csv";
		String testFile = "EventLogs\\\\DFCI_Test_May.csv";
		Float[] thresholds = new Float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f };

		/*
		float min = 0.01f;
		float max = 1.0f;
		float increment = 0.01f;
		List<Float> values = new ArrayList<>();
		for (float value = min; value <= max; value+=increment){
			values.add(value);
		}
		//values.add(1.0f);
		Float[] thresholds = values.toArray(new Float[0]);
		*/



    	logManager.readConfiguration(new FileInputStream("./app.properties"));
    	logger.info("started application");
    	    	    	
        try {

			AdaptiveNoiseBenchmarkConfiguration configuration = AdaptiveNoiseBenchmarkConfiguration.getBuilder()
					.useCrossValidation(false)
					.setNoiseThresholds(thresholds)
					.addWeights(Weights.getRangePrecision(0.1))
					.setPreExecuteFilter(false)
					.setLogSplitter(InductiveLogSplitting.class) //InductiveCutSplitting
					.build();
			IBenchmark benchmark = new AdaptiveNoiseBenchmarkDfci(trainFile, testFile, configuration, 10);
			benchmark.run();

        } catch (Exception ex) {
        	logger.log(Level.SEVERE, "exception when trying to train/evaluate the miner", ex);
        }
        
        logger.info("ended application");
    }
}