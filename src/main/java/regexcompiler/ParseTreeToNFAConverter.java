package regexcompiler;

import java.util.*;

import nfa.NFAGraph;
import nfa.NFAVertexND;
import nfa.NFAEdge;
import analysis.*;
import regexcompiler.RegexQuantifiableOperator.QuantifierType;
import regexcompiler.ParseTree.TreeNode;
import regexcompiler.RegexQuantifiableOperator.RegexPlusOperator;
import regexcompiler.RegexQuantifiableOperator.RegexQuestionMarkOperator;
import regexcompiler.RegexQuantifiableOperator.RegexStarOperator;


public abstract class ParseTreeToNFAConverter implements NFACreator {

	/* Maps the state in the NFAGraph to the NFAGraph representing the lookaround pattern. */
	private HashMap<NFAVertexND, NFAGraph> lookaroundStates;

	/* so we can constantly generate distinct state names (so they do not get over written) */
	private int stateCounter;
	private int lookAroundStateCounter;

	protected NFAVertexND nextState() {
		NFAVertexND newState = new NFAVertexND("q" + stateCounter);
		stateCounter++;
		return newState;
	}

	protected NFAVertexND nextLookAroundState() {
		NFAVertexND newLookAroundState = new NFAVertexND("l" + lookAroundStateCounter);
		lookAroundStateCounter++;
		return newLookAroundState;
	}
	

	
	protected static final int MAX_REPETITION = Integer.MAX_VALUE;

	protected ParseTreeToNFAConverter() {
		lookaroundStates = new HashMap<NFAVertexND, NFAGraph>();
		stateCounter = 0;
		lookAroundStateCounter = 0;
	}

	public NFAGraph convertParseTree(ParseTree parseTree) {
		
		TreeNode root = parseTree.getRoot();
		NFAGraph nfaGraph = dfsBuild(root);
		//System.out.println("Before look around intersection: " + nfaGraph);
		nfaGraph = performLookAroundIntersection(nfaGraph);
		nfaGraph = renameNFAStates(nfaGraph);
		//System.out.println("After look around intersection: " + nfaGraph);
		return nfaGraph;
	}
	
	private NFAGraph dfsBuild(TreeNode currentNode) {
		NFAGraph newNfaGraph;
		RegexToken regexToken = currentNode.getRegexToken();
		Iterator<TreeNode> childIterator = currentNode.getChildren().iterator();
		switch (regexToken.getTokenType()) {
		case OPERATOR:
				RegexOperator regexOperator = (RegexOperator) regexToken;
				switch (regexOperator.getOperatorType()) {
				case STAR: {
					RegexStarOperator starOperator = (RegexStarOperator) regexOperator;
					TreeNode operandNode = childIterator.next();
					NFAGraph subgraph = dfsBuild(operandNode);
					newNfaGraph = starNFA(subgraph, starOperator);
					break;
				}
				case PLUS: {
					RegexPlusOperator plusOperator = (RegexPlusOperator) regexOperator;
					TreeNode operandNode = childIterator.next();
					NFAGraph subgraph = dfsBuild(operandNode);
					newNfaGraph = plusNFA(subgraph, plusOperator);
					break;
				}
				case COUNT_CLOSURE: {
					RegexCountClosureOperator countClosureOperator = (RegexCountClosureOperator) regexOperator;
					TreeNode operandNode = childIterator.next();
					NFAGraph subgraph = dfsBuild(operandNode);
					newNfaGraph = countClosureNFA(subgraph, countClosureOperator);
					break;
				}					
				case QUESTION_MARK: {
					RegexQuestionMarkOperator questionMarkOperator = (RegexQuestionMarkOperator) regexOperator;
					TreeNode operandNode = childIterator.next();
					NFAGraph subgraph = dfsBuild(operandNode);
					newNfaGraph = questionMarkNFA(subgraph, questionMarkOperator);
					break;
				}					
				case UNION: {
					TreeNode operandNode1 = childIterator.next();
					TreeNode operandNode2 = childIterator.next();
					NFAGraph subgraph1 = dfsBuild(operandNode1);
					NFAGraph subgraph2 = dfsBuild(operandNode2);
					newNfaGraph = unionNFAs(subgraph1, subgraph2);
					break;
				}
				case JOIN: {
					TreeNode operandNode1 = childIterator.next();
					TreeNode operandNode2 = childIterator.next();
					NFAGraph subgraph1 = dfsBuild(operandNode1);
					NFAGraph subgraph2 = dfsBuild(operandNode2);
					newNfaGraph = joinNFAs(subgraph1, subgraph2);
					break;
				}
				default:
					throw new RuntimeException("Unknown operator type.");
				}
			break;
		case SUBEXPRESSION:
			RegexSubexpression<?> regexSubexpression = (RegexSubexpression<?>) regexToken;
			switch (regexSubexpression.getSubexpressionType()) {
			case CHARACTER_CLASS: {
				RegexCharacterClass regexCharacterClass = (RegexCharacterClass) regexSubexpression;
				newNfaGraph = createBaseCaseSymbol(regexCharacterClass.toString());
				break;
			}
			case ESCAPED_SYMBOL: {
				RegexEscapedSymbol regexEscapedSymbol = (RegexEscapedSymbol) regexSubexpression;
				newNfaGraph = createBaseCaseSymbol(regexEscapedSymbol.toString());
				break;
			}
			case GROUP:
				RegexGroup regexGroup = (RegexGroup) regexSubexpression;
				switch (regexGroup.getGroupType()) {
				case NORMAL:
				case NONCAPTURING: {
					TreeNode child = childIterator.next();
					newNfaGraph = dfsBuild(child);					
					break;
				}
				/* The only difference between these should be where the join comes.
				   Positive and negative look ahead the same from static analyses point of view. */
				case NEGLOOKAHEAD:
				case POSLOOKAHEAD: {
					TreeNode child = childIterator.next();
					NFAVertexND lookAroundState = nextLookAroundState();
					newNfaGraph = createBaseCaseLookAround(lookAroundState);
					NFAGraph lookAroundPatternNFA = dfsBuild(child);
					lookAroundPatternNFA = joinNFAs(lookAroundPatternNFA, createWildCardStarNFA(regexToken.getIndex()));
					lookaroundStates.put(lookAroundState, lookAroundPatternNFA);
					break;
				}
				case NEGLOOKBEHIND:
				case POSLOOKBEHIND: {
					TreeNode child = childIterator.next();
					NFAVertexND lookAroundState = nextLookAroundState();
					newNfaGraph = createBaseCaseLookAround(lookAroundState);
					NFAGraph lookAroundPatternNFA = dfsBuild(child);
					lookAroundPatternNFA = joinNFAs(createWildCardStarNFA(regexToken.getIndex()), lookAroundPatternNFA);
					lookaroundStates.put(lookAroundState, lookAroundPatternNFA);
					break;
				}
				default:
					throw new RuntimeException("Unknown Group type.");
				} // End switch group type
				break;
			case SYMBOL: {
				RegexSymbol regexSymbol = (RegexSymbol) regexSubexpression;
				String content = regexSymbol.getSubexpressionContent();
				newNfaGraph = createBaseCaseSymbol(content);
				break;
			}
			default:
				throw new RuntimeException("Unknown Subexpression type.");
				//break;
			} // End switch subexpression type
			break;
		default:
			throw new RuntimeException("Unknown Token type.");
			//break;
		} // End switch token type
		return newNfaGraph;
	}

	protected NFAVertexND deriveVertex(NFAGraph m, NFAVertexND v) {
		String newName = "" + v.getStateNumberByDimension(1).charAt(0);
		int i = 0;
		while (m.containsVertex(v)) {
			v = new NFAVertexND(newName + i);
			i++;
		}
		return v;
	}

	private NFAGraph createWildCardStarNFA(int index) {
		NFAGraph wildCardStar = createBaseCaseSymbol(".");
		return starNFA(wildCardStar, new RegexQuantifiableOperator.RegexStarOperator(QuantifierType.GREEDY, index));
	}

	private NFAGraph performLookAroundIntersection(NFAGraph nfaGraph) {
		NFAGraph intersectedGraph = nfaGraph;

		/* Positive look ahead intersection */
		for (Map.Entry<NFAVertexND, NFAGraph> kv : lookaroundStates.entrySet()) {
			NFAVertexND lookAroundState = kv.getKey();
			NFAGraph lookAroundNFA = kv.getValue();
			intersectedGraph = joinNFAs(intersectedGraph, performLookAheadIntersection(nfaGraph.copy(), lookAroundState, lookAroundNFA));
		}

		return intersectedGraph;

	}

	private NFAGraph performLookAheadIntersection(NFAGraph nfa, NFAVertexND lookAroundState, NFAGraph lookAroundNFA) {
		NFAGraph intersectedNFA = nfa.copy();
		NFAVertexND oldInitialState = intersectedNFA.getInitialState();
		intersectedNFA.setInitialState(lookAroundState);
		/* Trim away states that cannot be affected by the lookAround */
		HashSet<NFAVertexND> trimmedStates = new HashSet<NFAVertexND>();
		intersectedNFA = NFAAnalysisTools.makeTrimFromStart(intersectedNFA);
		for (NFAVertexND v : nfa.vertexSet()) {
			if (!intersectedNFA.containsVertex(v)) {
				trimmedStates.add(v);
			}
		}

		intersectedNFA = NFAAnalysisTools.productConstructionAFB(intersectedNFA, lookAroundNFA);

		/* Flatten the intersection */
		intersectedNFA = NFAAnalyserFlattening.flattenNFA(intersectedNFA);

		/* Put trimmed states back and connect them */
		Set<NFAVertexND> intersectionVertices = intersectedNFA.vertexSet();
		for (NFAVertexND v : trimmedStates) {
			intersectedNFA.addVertex(v);
		}
		/* Add the edges of the trimmed states */
		for (NFAVertexND v : trimmedStates) {
			for (NFAEdge e : nfa.outgoingEdgesOf(v)) {
				NFAVertexND target = e.getTargetVertex();
				if (trimmedStates.contains(target)) {
					NFAEdge newEdge = new NFAEdge(v, target, e.getTransitionLabel());
					intersectedNFA.addEdge(newEdge);
				} else {
					for (NFAVertexND intersectionVertex : intersectionVertices) {
						if (intersectionVertex.getStateByDimension(1).equals(target)) {
							NFAEdge newEdge = new NFAEdge(v, intersectionVertex, e.getTransitionLabel());
							intersectedNFA.addEdge(newEdge);
						}
					}
				}
			}
		}

		for (NFAVertexND v : intersectedNFA.vertexSet()) {
			if (v.getStateByDimension(1).equals(oldInitialState)) {
				intersectedNFA.setInitialState(v);
			}
		}

		return intersectedNFA;
	}


	private NFAGraph renameNFAStates(NFAGraph nfa) {
		NFAGraph renamedNFA = new NFAGraph();
		HashMap<NFAVertexND, NFAVertexND> renamingMap = new HashMap<NFAVertexND, NFAVertexND>();
		for (NFAVertexND v : nfa.vertexSet()) {
			NFAVertexND renamedState = nextState();
			renamingMap.put(v, renamedState);
			renamedNFA.addVertex(renamedState);
		}
		renamedNFA.setInitialState(renamingMap.get(nfa.getInitialState()));
		for (NFAVertexND v : nfa.getAcceptingStates()) {
			renamedNFA.addAcceptingState(renamingMap.get(v));
		}

		for (NFAEdge e : nfa.edgeSet()) {
			NFAVertexND source = renamingMap.get(e.getSourceVertex());
			NFAVertexND target = renamingMap.get(e.getTargetVertex());
			NFAEdge newEdge = new NFAEdge(source, target, e.getTransitionLabel());
			renamedNFA.addEdge(newEdge);
		}

		return renamedNFA;
	}
}
