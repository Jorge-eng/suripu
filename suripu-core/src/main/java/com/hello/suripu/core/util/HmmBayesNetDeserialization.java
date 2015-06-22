package com.hello.suripu.core.util;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.algorithm.bayes.BetaDiscreteWithEventOutput;
import com.hello.suripu.algorithm.bayes.BetaDistribution;
import com.hello.suripu.algorithm.bayes.ModelWithDiscreteProbabiltiesAndEventOccurence;
import com.hello.suripu.algorithm.bayes.MultipleEventModel;
import com.hello.suripu.algorithm.hmm.ChiSquarePdf;
import com.hello.suripu.algorithm.hmm.DiscreteAlphabetPdf;
import com.hello.suripu.algorithm.hmm.GammaPdf;
import com.hello.suripu.algorithm.hmm.GaussianPdf;
import com.hello.suripu.algorithm.hmm.HiddenMarkovModel;
import com.hello.suripu.algorithm.hmm.HmmPdfInterface;
import com.hello.suripu.algorithm.hmm.PdfComposite;
import com.hello.suripu.algorithm.hmm.PoissonPdf;
import com.hello.suripu.api.datascience.SleepHmmBayesNetProtos;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by benjo on 6/21/15.
 */
public class HmmBayesNetDeserialization {
    final static int MAX_NUM_HMM_STATES = 500;

    /*
    *  Goal is to create a bunch of HMMs, which decode some sensor data.
    *  The state sequences map to a bunch of conditional probabilities conditioned on a binary probability
    *  of some occurrence (on bed, sleeping, etc.).  Binary probability = ( P, !P)
    *
    *  Conditional probabilities?  It'll be a Beta-discrete implementation of a MultipleEventModel
    *  It'll live in a map, where the key is the model it came from
    *
    *
    * */

    public static class SensorDataReductionAndInterpretation {
        public final Map<String,HiddenMarkovModel> hmmByModelName;
        public final Map<String,MultipleEventModel> interpretationByOutputName;
        public final Map<String,String> modelNameMappedToOutputName;

        public SensorDataReductionAndInterpretation(Map<String, HiddenMarkovModel> hmmByModelName, Map<String, MultipleEventModel> interpretationByOutputName, Map<String, String> modelNameMappedToOutputName) {
            this.hmmByModelName = hmmByModelName;
            this.interpretationByOutputName = interpretationByOutputName;
            this.modelNameMappedToOutputName = modelNameMappedToOutputName;
        }
    }

    static SensorDataReductionAndInterpretation Deserialize(final SleepHmmBayesNetProtos.HmmBayesNet proto) {
        final SleepHmmBayesNetProtos.MeasurementParams params = proto.getMeasurementParameters();

        /*  FIRST MAKE THE HMMS */
        final Map<String,HiddenMarkovModel> hmmMapById = Maps.newHashMap();

        for (SleepHmmBayesNetProtos.HiddenMarkovModel model : proto.getIndependentHmmsList()) {
            hmmMapById.put(model.getId(),getHmm(model));
        }

        /* SECOND, MAKE THE CONDITIONAL PROBABILITY MODELS  */
        final Map<String,String> modelNamesToOutputNames = Maps.newHashMap();
        final Map<String,MultipleEventModel> interpretationByOutputName = Maps.newHashMap();

        for (final SleepHmmBayesNetProtos.CondProbs condProbs : proto.getConditionalProbabilitiesList()) {
            final String modelId = condProbs.getModelId();
            final String outputId = condProbs.getOutputId();
            modelNamesToOutputNames.put(modelId, outputId);

            //populate defaults
            if (interpretationByOutputName.get(outputId) == null) {
                interpretationByOutputName.put(outputId,new MultipleEventModel(2)); //binary distriubtions -- 2 discrete probs
            }
        }


        /*
        {
            //create mutliple event model
            final List<ModelWithDiscreteProbabiltiesAndEventOccurence> betaDists = Lists.newArrayList();

            final List<BetaDistribution> condProbsAsBetaDistributions = Lists.newArrayList();
            for (SleepHmmBayesNetProtos.BetaCondProb betaCondProb : condProbs.getProbsList()) {
                condProbsAsBetaDistributions.add(new BetaDistribution(betaCondProb.getAlpha(),betaCondProb.getBeta()));
            }

            BetaDiscreteWithEventOutput betaDiscreteWithEventOutput = new BetaDiscreteWithEventOutput(condProbsAsBetaDistributions);


            multipleEventModel.addModel();

        }
        */




      //  return new SensorDataReductionAndInterpretation(hmmMapById,something,modelNamesToOutputNames);
        return null;
    }

    private static HiddenMarkovModel getHmm(final SleepHmmBayesNetProtos.HiddenMarkovModel model) {
        final int numStates = model.getNumStates();
        final double [][] A = getMatrix(model.getStateTransitionMatrixList(),numStates);

        final double [] initProbs = new double[numStates];
        Arrays.fill(initProbs,1.0);

        final HmmPdfInterface [] models = getObsModels(model.getObservationModelList(),numStates);

        int numFreeParams = 0;
        for (HmmPdfInterface m : models) {
            numFreeParams += m.getNumFreeParams();
        }

        HiddenMarkovModel hmm = new HiddenMarkovModel(numStates,A,initProbs,models,numFreeParams);

        return hmm;
    }

    private static HmmPdfInterface [] getObsModels(List<SleepHmmBayesNetProtos.ObsModel> obsModels, final int numStates) {

        final Map<Integer,PdfComposite> composites = Maps.newHashMap();

        int highestStateNumber = 0;
        for (SleepHmmBayesNetProtos.ObsModel model : obsModels) {
            final Optional<HmmPdfInterface> pdf = getPdfFromObsModel(model);

            if (!pdf.isPresent()) {
                //TODO LOG ERRROR
                continue;
            }

            final int stateIdx = model.getStateIndex();
            if (composites.get(stateIdx) == null) {
                composites.put(stateIdx,new PdfComposite());
            }

            composites.get(stateIdx).addPdf(pdf.get());

            if (model.getStateIndex() > highestStateNumber) {
                highestStateNumber = model.getStateIndex();
            }
        }

        if (highestStateNumber >= numStates) {
            return new HmmPdfInterface[0];
        }

        HmmPdfInterface [] pdfs = new HmmPdfInterface[numStates];
        for (Integer iState = 0; iState < numStates; iState++) {

            if (composites.get(iState) == null) {
                //TODO LOG ERRROR or THROW or SOMETHING
                //no gaps!
                return new HmmPdfInterface[0];
            }

            pdfs[iState] = composites.get(iState);


        }

        return pdfs;
    }

    private static double [][] getMatrix(final List<Double> matAsVec, final int ncols) {
        final int nrows = matAsVec.size() / ncols;

        double [][] mat = new double[nrows][ncols];

        for (int j = 0; j < nrows; j++) {
            for (int i = 0; i < ncols; i++) {
                final int idx = j * ncols + i;
                mat[j][i] = matAsVec.get(idx);
            }
        }

        return mat;
    }

    private static Optional<HmmPdfInterface> getPdfFromObsModel(final SleepHmmBayesNetProtos.ObsModel obsModel) {

        final int measNumber = obsModel.getMeasType().getNumber();

        if (obsModel.hasGaussian()) {
            return Optional.of((HmmPdfInterface)new GaussianPdf(obsModel.getGaussian().getMean(),obsModel.getGaussian().getStddev(),measNumber));
        }

        if (obsModel.hasAlphabet()) {
            final int N = obsModel.getAlphabet().getProbabilitiesCount();
            final List<Double> alphabetProbs = Lists.newArrayList();

            for (int i = 0; i < N; i++) {
                alphabetProbs.add(obsModel.getAlphabet().getProbabilities(i));
            }

            return Optional.of((HmmPdfInterface)new DiscreteAlphabetPdf(alphabetProbs,measNumber));
        }

        if (obsModel.hasChisquare()) {
            return Optional.of((HmmPdfInterface)new ChiSquarePdf(obsModel.getChisquare().getMean(),measNumber));
        }

        if (obsModel.hasGamma()) {
            return Optional.of((HmmPdfInterface)new GammaPdf(obsModel.getGamma().getMean(),obsModel.getGamma().getStddev(),measNumber));
        }

        if (obsModel.hasPoisson()) {
            return Optional.of((HmmPdfInterface)new PoissonPdf(obsModel.getPoisson().getMean(),measNumber));
        }


        return  Optional.absent();

    }
}
