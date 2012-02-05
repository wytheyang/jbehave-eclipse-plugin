package org.technbolts.jbehave.eclipse.util;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.technbolts.eclipse.util.JDTUtils;
import org.technbolts.jbehave.eclipse.Activator;
import org.technbolts.jbehave.eclipse.JBehaveProjectRegistry;
import org.technbolts.jbehave.eclipse.PotentialStep;
import org.technbolts.util.HasHTMLComment;
import org.technbolts.util.Visitor;

import fj.Ord;
import static fj.data.List.iterableList;

public class StepLocator {
    
    public interface Provider {
        StepLocator getStepLocator();
    }
    
    public static StepLocator getStepLocator(IProject project) {
        return new StepLocator(project);
    }
    
    private IProject project;
    private StepLocator(IProject project) {
        this.project = project;
    }
    
    public static class WeightedCandidateStep implements Comparable<WeightedCandidateStep>, HasHTMLComment {
        public final PotentialStep potentialStep;
        public final float weight;
        public WeightedCandidateStep(PotentialStep potentialStep, float weight) {
            super();
            this.potentialStep = potentialStep;
            this.weight = weight;
        }
        @Override
        public int compareTo(WeightedCandidateStep o) {
            return (weight>o.weight)?1:-1;
        }
        
        private String htmlComment;
        @Override
        public String getHTMLComment() {
            if(htmlComment==null) {
                try {
                    htmlComment = JDTUtils.getJavadocOf(potentialStep.method);
                } catch (Exception e) {
                    htmlComment = "No documentation found";
                }
            }
            return htmlComment;
        }
    }
    
    static boolean findCandidatesCheckStepType = true;
    
    /*
     *  When '$who' clicks on the '$button_id' button
     *  
     *  When 'Bob' clicks on the 'login' button
     *  When 'Bob' clicks on the ...
     *  When 'Bo 
     * 
     */
    public Iterable<WeightedCandidateStep> findCandidatesStartingWith(final String stepLine) {
        
        try {
            final String searchedType = LineParser.stepType(stepLine);
            final String stepEntry = LineParser.extractStepSentence(stepLine);
            
            Visitor<PotentialStep, WeightedCandidateStep> findOne = new Visitor<PotentialStep, WeightedCandidateStep>() {
                @Override
                public void visit(PotentialStep candidate) {
                    boolean sameType = candidate.isTypeEqualTo(searchedType);
                    if(findCandidatesCheckStepType && !sameType) {
                        return;
                    }
                    
                    if(StringUtils.isBlank(stepEntry) && sameType) {
                        add(new WeightedCandidateStep(candidate, 0.1f));
                        return;
                    }
                    
                    float weight = candidate.weightOf(stepEntry);
                    if(weight>0) {
                        add(new WeightedCandidateStep(candidate, weight));
                    }
                    else {
                        //Activator.logInfo(">> Step (" + weight + ") rejected: " + candidate);
                    }
                }
            };
            traverseSteps(findOne);
            return findOne.getFounds();
        } catch (JavaModelException e) {
            e.printStackTrace();
            Activator.logError("Failed to find candidates for step <" + stepLine + ">", e);
        }
        return null;
    }
    
    /**
     * Returns the first {@link PotentialStep} found that match the step, ordered by priority.
     * Be careful that there can be several other {@link PotentialStep}s that fulfill the step too.
     * 
     * @param step
     * @return
     */
    public PotentialStep findFirstStep(final String step) {
        try {
            Visitor<PotentialStep, PotentialStep> matchingStepVisitor = new Visitor<PotentialStep, PotentialStep>() {
                @Override
                public void visit(PotentialStep candidate) {
                    boolean matches = candidate.matches(step);
                    if(matches) {
                        add(candidate);
                    }
                }
            };
            traverseSteps(matchingStepVisitor);
            return getFirstStepWithHighestPrio(matchingStepVisitor.getFounds());
        } catch (JavaModelException e) {
            Activator.logError("Failed to find candidates for step <" + step + ">", e);
        }
        return null;
    }

    /**
     * 
     * @param findOne
     * @return
     */
    PotentialStep getFirstStepWithHighestPrio(Iterable<PotentialStep> potentialSteps) {
        fj.data.List<Integer> collectedPrios = iterableList(potentialSteps).map(new PotentialStepPrioTransformer());
        if (collectedPrios.isEmpty()) {
            return null;
        }
        
        final int maxPrio = collectedPrios.maximum(Ord.intOrd);
        fj.data.List<PotentialStep> maxPrioSteps = iterableList(potentialSteps).filter(new PotentialStepPrioFilter(maxPrio));
        
        return maxPrioSteps.head();
    }
    
    public IJavaElement findMethod(final String step) {
        PotentialStep pStep = findFirstStep(step);
        if(pStep!=null)
            return pStep.method;
        else
            return null;
    }
    
    public void traverseSteps(Visitor<PotentialStep, ?> visitor) throws JavaModelException {
        JBehaveProjectRegistry.get().getOrCreateProject(project).traverseSteps(visitor);
    }
}
