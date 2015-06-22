package com.hello.suripu.algorithm.bayes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableListIterator;

import java.util.List;
import java.util.Map;

/**
 * Created by benjo on 6/16/15.
 */
public class MultipleEventModel {
    final List<Double> discreteProbabilties;
    final Map<String,List<ModelWithDiscreteProbabiltiesAndEventOccurence>> models;

    public MultipleEventModel(final int numDiscreteProbs) {
        this.models = Maps.newHashMap();
        discreteProbabilties = Lists.newArrayList();

        //default is uniform prior
        for (int iState = 0; iState < numDiscreteProbs; iState++) {
            discreteProbabilties.add(1.0 / (double)numDiscreteProbs);
        }
    }

    void setUniformPrior() {
        for (int iState = 0; iState < discreteProbabilties.size(); iState++) {
            discreteProbabilties.set(iState,1.0 / (double)discreteProbabilties.size());
        }
    }

    public void addModel(final String id, List<ModelWithDiscreteProbabiltiesAndEventOccurence> models) {
        this.models.put(id,models);
    }

    void setPriorForAllStatesBasedOnOneState(final int iState, final double prior) {
        final double notPrior = 1.0 - prior;

        if (discreteProbabilties.size() == 1) {
            discreteProbabilties.set(0,prior); //this is stupid, should always be at least size 2
            return;
        }

        final double allExceptSelected = notPrior / (double) (discreteProbabilties.size() - 1);

        for (int i = 0; i < discreteProbabilties.size(); i++) {
            discreteProbabilties.set(i,allExceptSelected);
        }

        discreteProbabilties.set(iState,prior);
    }



    /* get list of probabilties as they are sequentially updated with the events  */
    public List<List<Double>> getProbsFromEventSequence(final ImmutableList<Integer> events) {
        List<List<Double>> probs = Lists.newArrayList();

        //set prior
        ImmutableList<Double> prior = ImmutableList.copyOf(this.discreteProbabilties);

        for (final Integer event : events) {
            if (event < 0 || event >= models.size()) {
                //TODO LOG ERROR
                continue;
            }

            //iterate through each model
            for (final List<ModelWithDiscreteProbabiltiesAndEventOccurence> myModels : models.values()) {

                //get the model for this event happening
                final ModelWithDiscreteProbabiltiesAndEventOccurence myModel = myModels.get(event);

                //Bayes update
                final ImmutableList<Double> posterior = myModel.inferProbabilitiesGivenModel(prior);

                //posterior becomes prior
                prior = posterior;
            }


            //save posterior
            probs.add(ImmutableList.copyOf(prior));

        }

        return probs;
    }

    /*
     *  get joint probability of forwards and backwards: starting with prior forwards, and starting with prior backwards, and compute the joint of these probabilities
     *  at any given time
     *
     *  i.e.   P(A_forwards) * P(A_backwards) = P(A_forwards, A_backwards)
     *
     *  */
    public List<List<Double>> getJointOfForwardsAndBackwards(final ImmutableList<Integer> events) {

        //make backward events
        final UnmodifiableListIterator<Integer> iterator = events.listIterator(events.size());
        final List<Integer> backwardsEvents = Lists.newArrayList();
        while (iterator.hasPrevious()) {
            backwardsEvents.add(iterator.previous());
        }


        //do forwards
        final List<List<Double>> forwardProbs = getProbsFromEventSequence(events);

        //do backwards
        final List<List<Double>> backwardProbs = getProbsFromEventSequence(ImmutableList.copyOf(backwardsEvents));

        final List<List<Double>> joints = Lists.newArrayList();

        for (int iTimeForward = 0; iTimeForward < forwardProbs.size(); iTimeForward++) {
            final int iTimeBackward = forwardProbs.size() - iTimeForward - 1;

            final List<Double> forwardProb = forwardProbs.get(iTimeForward);
            final List<Double> backwardProb = backwardProbs.get(iTimeBackward);

            final List<Double> joint = Lists.newArrayList();
            for (int iState = 0; iState < forwardProb.size(); iState++) {
                joint.add(forwardProb.get(iState) * backwardProb.get(iState));
            }

            joints.add(joint);

        }

        return joints;
    }

    /* Apply to some subset of events where this label is valid  */
    public void inferModelParametersGivenEventsAndLabel(final ImmutableList<Double> label, final ImmutableList<Integer> events) {
        for (final Integer event : events) {
            if (event < 0 || event >= models.size()) {
                //TODO LOG ERROR
                continue;
            }

            //iterate through each model
            for (final List<ModelWithDiscreteProbabiltiesAndEventOccurence> myModels : models.values()) {

                //get the model for this event happening
                final ModelWithDiscreteProbabiltiesAndEventOccurence myModel = myModels.get(event);

                //updates state of model
                myModel.inferModelGivenObservedProbabilities(label);
            }
        }
    }
}
