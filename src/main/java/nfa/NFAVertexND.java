package nfa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * A vertex of a graph representing a state in an NFA. The state can be
 * multidimensional.
 * 
 * @author N. H. Weideman
 *
 */
public class NFAVertexND implements Comparable<NFAVertexND> {

	private ArrayList<String> states;

	private int hashCode1 = 0x811c9dc5;

	private int hashCode2;

	/**
	 * @return The states contained in this state
	 */
	public ArrayList<String> getStates() {
		return new ArrayList<String>(states);
	}

	/**
	 * Returns the state at a specified dimension. Since the states are referred
	 * to as being at dimensions, they are one indexed.
	 * 
	 * @param dim
	 * @return The state number at the dimension
	 */
	public String getStateNumberByDimension(int dim) {
		return states.get(dim - 1);
	}

	/**
	 * Gets a state at a certain dimension
	 * 
	 * @param dim
	 *            The dimension
	 * @return The state at the dimension.
	 */
	public NFAVertexND getStateByDimension(int dim) {
		return new NFAVertexND(states.get(dim - 1));
	}

	/**
	 * Constructs a new state formed from the states from the dimensions between
	 * two indices.
	 * 
	 * @param fromIndex
	 * @param toIndex
	 * @return The new state
	 */
	public NFAVertexND getStateByDimensionRange(int fromIndex, int toIndex) {
		return new NFAVertexND(states.subList(fromIndex - 1, toIndex - 1));
	}

	/**
	 * Adds a state at a new dimension, one more than the current highest
	 * dimension.
	 * 
	 * @param state
	 *            The state to add.
	 */
	public void addState(String state) {
		addToStates(state);
	}

	/**
	 * @return The number of dimensions in this state
	 */
	public int getNumDimensions() {
		return states.size();
	}
	
	/**
	 * A constructor for creating the default one dimensional vertex with an integer parameter.
	 * 
	 * @param m1StateNumber
	 *            The state number at dimension one
	 */
	public NFAVertexND(int m1StateNumber) {
		this(String.valueOf(m1StateNumber));
	}

	/**
	 * A constructor for creating the default one dimensional vertex
	 * 
	 * @param m1StateNumber
	 *            The state number at dimension one
	 */
	public NFAVertexND(String m1StateNumber) {
		states = new ArrayList<String>();
		addToStates(m1StateNumber);
	}
	
	/**
	 * A constructor for creating the frequently used three dimensional vertex with an integer parameter.
	 * 
	 * @param m1StateNumber
	 *            The state number at dimension one
	 * @param m2StateNumber
	 *            The state number at dimension two
	 * @param m3StateNumber
	 *            The state number at dimension three
	 */
	public NFAVertexND(int m1StateNumber, int m2StateNumber, int m3StateNumber) {
		this(String.valueOf(m1StateNumber), String.valueOf(m2StateNumber), String.valueOf(m3StateNumber));
	}

	/**
	 * A constructor for creating the frequently used three dimensional vertex.
	 * 
	 * @param m1StateNumber
	 *            The state number at dimension one
	 * @param m2StateNumber
	 *            The state number at dimension two
	 * @param m3StateNumber
	 *            The state number at dimension three
	 */
	public NFAVertexND(String m1StateNumber, String m2StateNumber, String m3StateNumber) {
		states = new ArrayList<String>();
		addToStates(m1StateNumber);
		addToStates(m2StateNumber);
		addToStates(m3StateNumber);
	}
	
	/**
	 * A constructor for creating the frequently used five dimensional vertex.
	 * 
	 * @param m1StateNumber
	 *            The state number at dimension one
	 * @param m2StateNumber
	 *            The state number at dimension two
	 * @param m3StateNumber
	 *            The state number at dimension three
	 * @param m4StateNumber
	 *            The state number at dimension four
	 * @param m5StateNumber
	 *            The state number at dimension five
	 */
	public NFAVertexND(int m1StateNumber, int m2StateNumber, int m3StateNumber, int m4StateNumber, int m5StateNumber) {
		this(String.valueOf(m1StateNumber), String.valueOf(m2StateNumber), String.valueOf(m3StateNumber), String.valueOf(m4StateNumber), String.valueOf(m5StateNumber));
	}

	/**
	 * A constructor for creating the frequently used five dimensional vertex.
	 * 
	 * @param m1StateNumber
	 *            The state number at dimension one
	 * @param m2StateNumber
	 *            The state number at dimension two
	 * @param m3StateNumber
	 *            The state number at dimension three
	 * @param m4StateNumber
	 *            The state number at dimension four
	 * @param m5StateNumber
	 *            The state number at dimension five
	 */
	public NFAVertexND(String m1StateNumber, String m2StateNumber, String m3StateNumber, String m4StateNumber, String m5StateNumber) {
		states = new ArrayList<String>();
		addToStates(m1StateNumber);
		addToStates(m2StateNumber);
		addToStates(m3StateNumber);
		addToStates(m4StateNumber);
		addToStates(m5StateNumber);
	}
	
	/**
	 * A constructor for creating one multidimensional vertex from a list of integers
	 * 
	 * @param mStates
	 *            The vertices
	 */
	public NFAVertexND(int... mStates) {
		states = new ArrayList<String>();
		for (int i : mStates) {
			String state = String.valueOf(i);
			addToStates(state);
		}
	}

	/**
	 * A constructor for creating one multidimensional vertex from a list of strings
	 * 
	 * @param mStates
	 *            The vertices
	 */
	public NFAVertexND(String... mStates) {

		states = new ArrayList<String>();
		for (String i : mStates) {
			addToStates(i);
		}
	}
	
	/**
	 * A constructor for creating one multidimensional vertex from
	 * multidimensional vertices
	 * 
	 * @param mStates
	 *            The vertices
	 */
	public NFAVertexND(NFAVertexND... mStates) {

		states = new ArrayList<String>();
		for (NFAVertexND nfavnd : mStates) {
			for (String i : nfavnd.states) {
				addToStates(i);
			}
		}
	}

	/**
	 * A constructor for creating one multidimensional vertex from a collection
	 * of multidimensional vertices
	 * 
	 * @param states
	 *            The collection of vertices
	 */
	public NFAVertexND(Collection<String> states) {

		this.states = new ArrayList<String>();
		for (String i : states) {
			this.addToStates(i);
		}
	}
	
	/**
	 * A constructor for creating one multidimensional vertex from a set
	 * of multidimensional vertices
	 * 
	 * @param states
	 *            The collection of vertices
	 */
	public NFAVertexND(Set<NFAVertexND> states) {

		this.states = new ArrayList<String>();
		for (NFAVertexND i : states) {
			for (String s : i.states) {
				this.addToStates(s);
			}
		}
	}

	/**
	 * @return A new instance of an NFAEdge equal to this instance.
	 */
	public NFAVertexND copy() {
		NFAVertexND c = new NFAVertexND(states);
		return c;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("(");
		Iterator<String> i0 = states.iterator();
		while (i0.hasNext()) {
			sb.append(i0.next());
			if (i0.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (!o.getClass().isAssignableFrom(this.getClass())) {
			return false;
		}
		NFAVertexND p = (NFAVertexND) o;
		return hashCode1 == p.hashCode1 && hashCode2 == p.hashCode2 && states.size() == p.states.size();

	}

	@Override
	public int hashCode() {
		return hashCode1 + hashCode2 + states.size();
	}

	private void addToStates(String state) {
		states.add(state);
		for (int i = 0; i < state.length(); i++) {
			hashCode1 = (0x811c9dc5 * hashCode1) ^ state.charAt(i);
			hashCode2 = 31 * hashCode2 + state.charAt(i);
		}
	}

	@Override
	public int compareTo(NFAVertexND o) {
		int rc = Integer.compare(states.size(), o.states.size());
		if (rc != 0) {
			return rc;
		}

		for (int i = 0; i < states.size(); i++) {
			rc = states.get(i).compareTo(o.states.get(i));
			if (rc != 0) {
				return rc;
			}
		}

		return 0;
	}

}
