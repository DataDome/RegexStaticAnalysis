package analysis;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import analysis.EdaAnalysisResults.EdaCases;
import analysis.IdaAnalysisResults.IdaCases;
import analysis.NFAAnalyserInterface.EdaAnalysisResultsNoEda;
import analysis.NFAAnalyserInterface.IdaAnalysisResultsNoIda;

import analysis.AnalysisSettings.PriorityRemovalStrategy;

import nfa.NFAGraph;
import nfa.NFAEdge;
import nfa.NFAVertexND;

/**
 * An analyser for finding patterns that can cause catastrophic backtracking.
 * 
 * @author N. H. Weideman
 *
 */
public class NFAAnalyserMerging extends NFAAnalyser {
	
	public NFAAnalyserMerging(PriorityRemovalStrategy priorityRemovalStrategy, int maxComplexity) {
		super(priorityRemovalStrategy, maxComplexity);
	}

	private EdaAnalysisResults testCaseESCC(NFAGraph originalM, LinkedList<NFAGraph> sccsInOriginal, Map<NFAVertexND, NFAGraph> esccs) {

		/* mapping SCCs to the ESCC's in them */
		HashMap<NFAGraph, LinkedList<NFAVertexND>> sccToMergedESCCStatesMap = new HashMap<NFAGraph, LinkedList<NFAVertexND>>();
		for (NFAGraph currentSCCInOriginal : sccsInOriginal) {
			for (NFAVertexND currentVertex : currentSCCInOriginal.vertexSet()) {
				/* if a vertex in this SCC becomes a merged ESCC */
				if (esccs.containsKey(currentVertex)) {
					LinkedList<NFAVertexND> newList;
					if (sccToMergedESCCStatesMap.containsKey(currentVertex)) {
						newList = sccToMergedESCCStatesMap.get(currentVertex);
					} else {
						newList = new LinkedList<NFAVertexND>();
					}
					//LinkedList<NFAVertexND> newList = sccToMergedESCCStatesMap.getOrDefault(currentVertex, new LinkedList<NFAVertexND>());
					newList.add(currentVertex);
					sccToMergedESCCStatesMap.put(currentSCCInOriginal, newList);
				}
			}
		}
		for (NFAGraph currentSCCInOriginal : sccsInOriginal) {
			/*
			 * find esccs in this scc (in merged). The OrDefault lets it return
			 * an empty list to save a null check
			 */
			LinkedList<NFAVertexND> esscsInCurrentSCC;
			if (sccToMergedESCCStatesMap.containsKey(currentSCCInOriginal)) {
				esscsInCurrentSCC = sccToMergedESCCStatesMap.get(currentSCCInOriginal);
			} else {
				esscsInCurrentSCC = new LinkedList<NFAVertexND>();
			}
			//LinkedList<NFAVertexND> esscsInCurrentSCC = sccToMergedESCCStatesMap.getOrDefault(currentSCCInOriginal, new LinkedList<NFAVertexND>());

			for (NFAVertexND mergedESCCInSCC : esscsInCurrentSCC) {
				NFAGraph currentESCC = esccs.get(mergedESCCInSCC);

				/*
				 * find all the entrance and exit states of the escc and map
				 * them to their entrance, exit edges
				 */
				HashMap<NFAVertexND, NFAEdge> entranceStates = new HashMap<NFAVertexND, NFAEdge>();
				HashMap<NFAVertexND, NFAEdge> exitStates = new HashMap<NFAVertexND, NFAEdge>();

				for (NFAVertexND v : currentESCC.vertexSet()) {
					/*
					 * we defined an entrance/exit edge to still be in the scc
					 * of the escc
					 */

					for (NFAEdge e : currentSCCInOriginal.incomingEdgesOf(v)) {
						/*
						 * if the edge comes from a vertex not in the escc (but
						 * in the scc), or is a non-epsilon transition.
						 */
						if (!currentESCC.containsEdge(e)) {
							// System.out.println(e.getSourceVertex() + "->" +
							// e.getTargetVertex());
							if (!entranceStates.containsKey(v)) {
								entranceStates.put(v, e);
							}
						}
					}
					for (NFAEdge e : currentSCCInOriginal.outgoingEdgesOf(v)) {
						/*
						 * if the edge goes to a vertex not in the escc (but in
						 * the scc), or is a non-epsilon transition.
						 */
						if (!currentESCC.containsEdge(e)) {
							// System.out.println(e.getSourceVertex() + "->" +
							// e.getTargetVertex());
							if (!exitStates.containsKey(v)) {
								exitStates.put(v, e);
							}
						}
					}
				}

				/*
				 * if there is more than one walk between an entrance to the
				 * escc and and exit to the escc we have EDA. If a vertex is
				 * both an entrance and an exit, we count staying on the same
				 * vertex as a path.
				 */

				for (NFAVertexND start : entranceStates.keySet()) {
					HashMap<NFAVertexND, Integer> pathsToOtherVertices = NFAAnalysisTools.numWalksFrom(currentESCC, start);
					/*
					 * map from other vertex to the number of paths to that
					 * vertex
					 */
					for (Map.Entry<NFAVertexND, Integer> kv : pathsToOtherVertices.entrySet()) {
						NFAVertexND p = kv.getKey();
						/* if the other vertex is an exit vertex */
						if (exitStates.containsKey(p)) {
							if (kv.getValue() > 1) {
								/* building the exploit string */
								/*
								 * find the scc of the merged escc in the merged
								 * graph
								 */
								 
								NFAEdge entranceEdge = entranceStates.get(start);
								NFAEdge exitEdge = exitStates.get(p);
								//System.out.println("Case: More than one walk through escc: " + entranceEdge.getTargetVertex() + " " + exitEdge.getSourceVertex());
								EdaAnalysisResultsESCC resultsObject = new EdaAnalysisResultsESCC(originalM, currentSCCInOriginal, entranceEdge, exitEdge);
								return resultsObject;
							}
						}
					}
				}
			}
		}

		return new EdaAnalysisResultsNoEda(originalM);
	}

	@Override
	protected EdaAnalysisResults calculateEdaAnalysisResults(NFAGraph originalM) {
		LinkedList<NFAGraph> sccsInOriginal = NFAAnalysisTools.getStronglyConnectedComponents(originalM, maxComplexity);
		if (sccsInOriginal == null) {
			return new TooComplexEdaAnalysisResults(originalM);
		}

		NFAGraph merged = originalM.copy();
		Map<NFAVertexND, NFAGraph> esccs = NFAAnalysisTools.mergeStronglyConnectedComponents(merged, true, maxComplexity);
		if (esccs == null) {
			return new TooComplexEdaAnalysisResults(originalM);
		}

		LinkedList<NFAGraph> sccsInMerged = NFAAnalysisTools.getStronglyConnectedComponents(merged, maxComplexity);
		if (sccsInMerged == null) {
			return new TooComplexEdaAnalysisResults(originalM);
		}

		EdaAnalysisResults toReturn = new EdaAnalysisResultsNoEda(originalM);
		/* Testing for parallel edges in scc in merged graph */
		toReturn = edaTestCaseParallel(originalM, sccsInMerged);
		if (toReturn.edaCase != EdaCases.NO_EDA) {
			return toReturn;
		}
		
		
		/* Testing for multiple paths through ESCCs */
		toReturn = testCaseESCC(originalM, sccsInOriginal, esccs);
		toReturn.setPriorityRemovalStrategy(PriorityRemovalStrategy.IGNORE);
		
		if (toReturn.edaCase != EdaCases.NO_EDA) {
			return toReturn;
		}
		
		/* Testing for multiple paths in PC */
		toReturn = edaTestCaseFilter(originalM, merged);
		toReturn.setPriorityRemovalStrategy(PriorityRemovalStrategy.IGNORE);
		
		return toReturn;
	}
	
	@Override
	protected EdaAnalysisResults calculateEdaUnprioritisedAnalysisResults(NFAGraph originalM) {
		NFAGraph merged = originalM.copy();
		if (NFAAnalysisTools.mergeStronglyConnectedComponents(merged, true, maxComplexity) == null) {
			return new TooComplexEdaAnalysisResults(originalM);
		}

		EdaAnalysisResults toReturn = edaUnprioritisedAnalysis(merged, maxComplexity);
		if (toReturn == null) {
			return new TooComplexEdaAnalysisResults(originalM);
		}

		toReturn.setPriorityRemovalStrategy(PriorityRemovalStrategy.UNPRIORITISE);
		return toReturn;
	}


	@Override
	protected IdaAnalysisResults calculateIdaAnalysisResults(NFAGraph originalM) {
		NFAGraph merged = originalM.copy();
		if (NFAAnalysisTools.mergeStronglyConnectedComponents(merged, true, maxComplexity) == null) {
			return new TooComplexIdaAnalysisResults(originalM);
		}
		
		IdaAnalysisResults toReturn = new IdaAnalysisResultsNoIda(originalM);
		toReturn.setPriorityRemovalStrategy(PriorityRemovalStrategy.IGNORE);

		/* Testing for multiple paths in PC */
		toReturn = idaTestCaseFilter(originalM, merged);
		if (toReturn == null) {
			return new TooComplexIdaAnalysisResults(originalM);
		}
		toReturn.setPriorityRemovalStrategy(PriorityRemovalStrategy.IGNORE);
				
		return toReturn;
	}

	@Override
	protected IdaAnalysisResults calculateIdaUnprioritisedAnalysisResults(NFAGraph originalM) {
		NFAGraph merged = originalM.copy();
		if (NFAAnalysisTools.mergeStronglyConnectedComponents(merged, true, maxComplexity) == null) {
			return new TooComplexIdaAnalysisResults(originalM);
		}

		IdaAnalysisResults toReturn = new IdaAnalysisResultsNoIda(originalM);
		toReturn = idaUnprioritisedAnalysis(merged);

		toReturn.setPriorityRemovalStrategy(PriorityRemovalStrategy.UNPRIORITISE);
		return toReturn;
	}	

}
