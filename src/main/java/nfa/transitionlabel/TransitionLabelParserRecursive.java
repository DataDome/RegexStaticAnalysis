package nfa.transitionlabel;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.regex.PatternSyntaxException;

import nfa.NFAEdge;
import util.RangeSet;
import util.RangeSet.Range;

/*
 * TODO: \Q ... \E For some reason Java throws an PatternSyntaxException: Unclosed character class on [\Q\E]
 * \ uhhhh (remove space)
 * (\Uhhhhhhhh not supported)
 * \0ooo There needn't be 3 o's 
 */

public class TransitionLabelParserRecursive {
	private static final int MIN_16UNICODE = 0;
	private static final int MAX_16UNICODE = 65536;

	private final String transitionLabelString;
	private char currentSymbol;
	private int index;
	private int depth;

	private CharacterPropertyParser characterPropertyParser;

	public TransitionLabelParserRecursive(String transitionLabelString) {
		this.transitionLabelString = transitionLabelString;
		this.index = 0;
		this.depth = 0;
	}

	private boolean consumeSymbol() {
		if (index >= transitionLabelString.length()) {
			return false;
		}

		currentSymbol = transitionLabelString.charAt(index);
		index++;

		return true;
	}

	private void consumeSymbolIfHasNext() {
		if (index < transitionLabelString.length()) {
			consumeSymbol();
		}
	}

	@SuppressWarnings("fallthrough")
	public TransitionLabel parseTransitionLabel() {
		TransitionLabel toReturn;
		RangeSet labelRanges;
		consumeSymbol();
		switch (currentSymbol) {
		case '.':
			labelRanges = CharacterClassTransitionLabel.predefinedRangeWildcard();
			toReturn = new CharacterClassTransitionLabel(labelRanges);
			break;
		case '[':
			/* parse character class */
			labelRanges = parseCharacterClass();
			if (depth != 0) {
				throw new PatternSyntaxException("Unclosed character class", transitionLabelString, index);
			}
			toReturn = new CharacterClassTransitionLabel(labelRanges);
			break;
		case '\\':
			/* parse predefined character class, or backslash */
			consumeSymbol();
			if (currentSymbol == '\\') {
				toReturn = new CharacterClassTransitionLabel("\\");
			} else if (currentSymbol == '-') {
				toReturn = new CharacterClassTransitionLabel("-");
			} else {
				RangeSet predefinedCharacterClassRangeSet = parsePredefinedCharacterClass(currentSymbol);
				toReturn = new CharacterClassTransitionLabel(predefinedCharacterClassRangeSet);
			}

			break;
		default:
			/*
			 * parse character, we send the entire string, for epsilon
			 * subscripts
			 */
			if (NFAEdge.isEpsilonCharacter(transitionLabelString)) {
				toReturn = new EpsilonTransitionLabel(transitionLabelString);
			} else {
				toReturn = new CharacterClassTransitionLabel(transitionLabelString);
			}

		}
		index = transitionLabelString.length();
		return toReturn;
	}
	@SuppressWarnings("fallthrough")
	private RangeSet parsePredefinedCharacterClass(char firstSymbol) {
		RangeSet toReturn = null;
		boolean complement = false;
		char c;
		switch (firstSymbol) {
		case 'a':
			consumeSymbolIfHasNext();
			return parseCharacterRange(((char) 7));
		case 'e':
			consumeSymbolIfHasNext();
			return parseCharacterRange(((char) 27));
		case 'f':
			consumeSymbolIfHasNext();
			return parseCharacterRange('\f');
		case 'n':
			consumeSymbolIfHasNext();
			return parseCharacterRange('\n');
		case 'r':
			consumeSymbolIfHasNext();
			return parseCharacterRange('\r');
		case 't':
			consumeSymbolIfHasNext();
			return parseCharacterRange('\t');
		case '\\':
			consumeSymbolIfHasNext();
			return parseCharacterRange('\\');
		case '\'':
			consumeSymbolIfHasNext();
			return parseCharacterRange('\'');
		case '\"':
			consumeSymbolIfHasNext();
			return parseCharacterRange('\"');
		case '[':
			consumeSymbolIfHasNext();
			return parseCharacterRange('[');
		case ']':
			consumeSymbolIfHasNext();
			return parseCharacterRange(']');
		case '-':
			consumeSymbolIfHasNext();
			return parseCharacterRange('-');
		case 'Q':
			return parseQuotedSequence();
		case '0':
			c = parseEscapedOctalCharacter();
			return parseCharacterRange(c);
		case 'u':
			c = parseEscapedUnicodeCharacter();
			consumeSymbolIfHasNext();
			return parseCharacterRange(c);
		case 'x':
			c = parseEscapedHexCharacter();
			consumeSymbolIfHasNext();
			return parseCharacterRange(c);
		case 'c':
			consumeSymbol();
			int charCode = (((currentSymbol - '@') % 128 + 128) % 128);
			c = (char) charCode;		
			consumeSymbol();
			return parseCharacterRange(c);
		case 'D':
			/* predefined class: non-digits */
			complement = true;
		case 'd':
			/* predefined class: digits */
			toReturn = CharacterClassTransitionLabel.predefinedRangeSetDigits();
			break;
		case 'S':
			/* predefined class: non-whitespace */
			complement = true;
		case 's':
			/* predefined class: whitespace */
			toReturn = CharacterClassTransitionLabel.predefinedRangeSetWhiteSpaces();
			break;
		case 'W':
			/* predefined class: non-word */
			complement = true;
		case 'w':
			/* predefined class: word */
			toReturn = CharacterClassTransitionLabel.predefinedRangeSetWordCharacters();
			break;
		case 'V':
			complement = true;
		case 'v':
			/* predefined class: vertical tab */
			toReturn = CharacterClassTransitionLabel.predefinedRangeSetVerticalTab();
			break;
		case 'H':
			complement = true;
		case 'h':
			toReturn = CharacterClassTransitionLabel.predefinedRangeSetHorizontalTab();
			break;
		case 'P':
			complement = true;
		case 'p':
			toReturn = parsePropertyCharacterClass();
			break;
		default:
			/*
			 * TODO: It seems any symbol except letters and numbers can be
			 * escaped, (other than those for predefined character classes, or
			 * escape characters)
			 */
			if ((currentSymbol >= 'A' && currentSymbol <= 'Z') || (currentSymbol >= 'a' && currentSymbol <= 'z') || (currentSymbol >= '0' && currentSymbol <= '9')) {
				throw new PatternSyntaxException("Illegal/unsupported escape sequence", transitionLabelString, index);
			} else {
				char symbol = currentSymbol;
				consumeSymbol();
				return parseCharacterRange(symbol);
			}

		}
		consumeSymbolIfHasNext();

		if (complement) {
			toReturn.complement();
		}
		return toReturn;
	}
	
	private RangeSet parseQuotedSequence() {
		RangeSet toReturn = new RangeSet(MIN_16UNICODE, MAX_16UNICODE);
		consumeSymbol();
		LinkedList<Range> symbolSequence = new LinkedList<Range>();
		char lastChar = currentSymbol;
		
		while (true) {
			if (currentSymbol == '\\') {
				if (!consumeSymbol()) {
					throw new PatternSyntaxException("Unclosed character class", transitionLabelString, index);
				}
				if (currentSymbol == 'E') {
					if (!consumeSymbol()) {
						throw new PatternSyntaxException("Unclosed character class", transitionLabelString, index);
					}
					break;
				} else {
					symbolSequence.add(toReturn.createRange('\\'));
				}
			}
			symbolSequence.add(toReturn.createRange(currentSymbol));
			lastChar = currentSymbol;
			if (!consumeSymbol()) {
				throw new PatternSyntaxException("Unclosed character class", transitionLabelString, index);
			}
		}
		toReturn.union(symbolSequence);
		if (currentSymbol == '-') {
			toReturn.union(parseCharacterRange(lastChar));
		}
		
		return toReturn;
	}
	
	private char parseEscapedOctalCharacter() {
		consumeSymbol();
		StringBuilder hexNumberStr = new StringBuilder();
		
		int i = 0;
		/* Read octal symbols until larger than allowed max up to a maximum of three characters */
		int tmpNum = 0;
		while (tmpNum < 0377 && currentSymbol >= '0' && currentSymbol <= '7' && i < 3) {
			
			hexNumberStr.append(currentSymbol);			
			tmpNum = Integer.parseInt(hexNumberStr.toString(), 8);
			i++;
			if (!consumeSymbol()) {
				break;
			}
		}

		try {
			int hexNumber = Integer.parseInt(hexNumberStr.toString(), 8);

			if (hexNumber >= MAX_16UNICODE) {
				throw new PatternSyntaxException("Hexadecimal codepoint is too big", transitionLabelString, index);
			}
			return ((char) hexNumber);
		} catch (NumberFormatException nfe) {
			throw new PatternSyntaxException("Illegal hexadecimal escape sequence", transitionLabelString, index);
		}
	}
	
	private char parseEscapedUnicodeCharacter() {
		consumeSymbol();
		StringBuilder hexNumberStr = new StringBuilder();
		/* Read next four symbols as hex number */
		hexNumberStr.append(currentSymbol);
		for (int i = 0; i < 4; i++) {
			consumeSymbol();
			hexNumberStr.append(currentSymbol);			
			
		}

		try {
			int hexNumber = Integer.parseInt(hexNumberStr.toString(), 16);

			if (hexNumber >= MAX_16UNICODE) {
				throw new PatternSyntaxException("Hexadecimal codepoint is too big", transitionLabelString, index);
			}
			return ((char) hexNumber);
		} catch (NumberFormatException nfe) {
			throw new PatternSyntaxException("Illegal hexadecimal escape sequence", transitionLabelString, index);
		}
	}
	
	private char parseEscapedHexCharacter() {
		consumeSymbol();
		StringBuilder hexNumberStr = new StringBuilder();
		if (currentSymbol == '{') {
			/* read until } is found */
			consumeSymbol();

			while (currentSymbol != '}') {
				hexNumberStr.append(currentSymbol);
				consumeSymbol();
			}
		} else {
			/* Read next two symbols as hex number */
			hexNumberStr.append(currentSymbol);
			consumeSymbol();
			hexNumberStr.append(currentSymbol);

		}
		try {
			int hexNumber = Integer.parseInt(hexNumberStr.toString(), 16);

			if (hexNumber >= MAX_16UNICODE) {
				throw new PatternSyntaxException("Hexadecimal codepoint is too big", transitionLabelString, index);
			}
			return ((char) hexNumber);
		} catch (NumberFormatException nfe) {
			throw new PatternSyntaxException("Illegal hexadecimal escape sequence", transitionLabelString, index);
		}
	}

	private RangeSet parsePropertyCharacterClass() {
		if (characterPropertyParser == null) {
			characterPropertyParser = new CharacterPropertyParser(transitionLabelString, index);
		} else {
			characterPropertyParser.setIndex(index);
		}

		RangeSet toReturn;

		consumeSymbol();
		if (currentSymbol != '{') {
			/* Single character character properties */
			toReturn = characterPropertyParser.parseCharacterProperty(Character.toString(currentSymbol));

		} else {
			StringBuilder sb = new StringBuilder();
			consumeSymbol(); /* eating the '{' */
			while (currentSymbol != '}') {
				sb.append(currentSymbol);
				consumeSymbol();
			}
			String characterPropertyString = sb.toString();
			toReturn = characterPropertyParser.parseCharacterProperty(characterPropertyString);
		}
		return toReturn;
	}

	private RangeSet parseCharacterClass() {
		depth++;
		/* eating [ */
		if (!consumeSymbol())  {
			throw new PatternSyntaxException("Unclosed character class", transitionLabelString, index);
		}
		boolean isComplement = false;
		if (currentSymbol == '^') {
			isComplement = true;
			consumeSymbol(); /* eating ^ */
		}

		RangeSet characterClassRangeSet = new RangeSet(MIN_16UNICODE, MAX_16UNICODE);
		if (currentSymbol == ']') {
			/*
			 * since we make the assumption that empty character classes i.e. []
			 * are not allowed, we treat ] as a literal character.
			 */
			characterClassRangeSet.union(createCharacterRange(']'));
			if (!consumeSymbol()) {
				throw new PatternSyntaxException("Unclosed character class", transitionLabelString, index);
			}
		}

		/*
		 * We, unlike Java, require that there must be at least one factor in
		 * the Character Class
		 */
		/* The ^ only applies to the first factor */
		characterClassRangeSet.union(parseCharacterClassFactor(characterClassRangeSet, isComplement));

		while (currentSymbol != ']') {
			/*
			 * this might be a problem, but the parseCharacterClassFactor will
			 * have to parse the &&
			 */
			RangeSet currentFactor = parseCharacterClassFactor(new RangeSet(MIN_16UNICODE, MAX_16UNICODE), false);
			characterClassRangeSet.intersection(currentFactor);
		}
		
		depth--;
		if (index < transitionLabelString.length()) {
			consumeSymbol();
		} else if (depth != 0) {
			throw new PatternSyntaxException("Unclosed character class", transitionLabelString, index);
		}
		return characterClassRangeSet;
	}

	/* leaves after a && or on a ] */
	private RangeSet parseCharacterClassFactor(RangeSet characterClassFactorRangeSet, boolean isComplement) {

		boolean factorComplete = false;
		while (!factorComplete) {

			if (currentSymbol == '[') {
				if (isComplement) {
					/* ^ only applies to first term of first factor */
					isComplement = false;
					/* ^ does not work if [ is directly after it */
					if (!characterClassFactorRangeSet.isEmpty()) { 
						characterClassFactorRangeSet.complement();
					}
				}
				RangeSet currentFactor = parseCharacterClass();
				characterClassFactorRangeSet.union(currentFactor);
			} else if (currentSymbol == ']') {
				if (isComplement) {
					characterClassFactorRangeSet.complement();
				}
				/* leaving the ] for the parseCC to consume */
				factorComplete = true; 
			} else if (currentSymbol == '&') {
				consumeSymbol(); /* eating the first & */

				if (currentSymbol == '&') {

					/* we found &&, end of factor */
					factorComplete = true;
					if (isComplement) {
						characterClassFactorRangeSet.complement();
					}
					consumeSymbol(); /* eating the second & */
				} else {
					/* parsing the eaten & */
					characterClassFactorRangeSet.union(parseCharacterRange('&')); 
				}
			} else if (currentSymbol == '\\') {
				consumeSymbol();
				/*
				 * for some reason predefined character classes do not count as
				 * nested character classes...
				 */
				characterClassFactorRangeSet.union(parsePredefinedCharacterClass(currentSymbol));

			} else {
				char firstSymbol = currentSymbol;
				consumeSymbol();
				characterClassFactorRangeSet.union(parseCharacterRange(firstSymbol));
			}
		}
		return characterClassFactorRangeSet;
	}

	/* firstSymbol is the symbol before currentSymbol */
	private RangeSet parseCharacterRange(char firstSymbol) {
		RangeSet characterRangeRangeSet;
		if (currentSymbol == '-') {
			if (index < transitionLabelString.length()) {
				consumeSymbol();
				if (currentSymbol == '\\') {
					consumeSymbol();
					switch (currentSymbol) {
					case 'a':
						characterRangeRangeSet = createCharacterRange(firstSymbol, ((char) 7));
						consumeSymbol();
						break;
					case 'e':
						characterRangeRangeSet = createCharacterRange(firstSymbol, ((char) 27));
						consumeSymbol();
						break;
					case 'f':
						characterRangeRangeSet = createCharacterRange(firstSymbol, '\f');
						consumeSymbol();
						break;
					case 'n':
						characterRangeRangeSet = createCharacterRange(firstSymbol, '\n');
						consumeSymbol();
						break;
					case 'r':
						characterRangeRangeSet = createCharacterRange(firstSymbol, '\r');
						consumeSymbol();
						break;
					case 't':
						characterRangeRangeSet = createCharacterRange(firstSymbol, '\t');
						consumeSymbol();
						break;
					case '[':
						characterRangeRangeSet = createCharacterRange(firstSymbol, '[');
						consumeSymbol();
						break;
					case ']':
						characterRangeRangeSet = createCharacterRange(firstSymbol, ']');
						consumeSymbol();
						break;
					case '\\':
						characterRangeRangeSet = createCharacterRange(firstSymbol, '\\');
						consumeSymbol();
						break;
					case '-':
						characterRangeRangeSet = createCharacterRange(firstSymbol, '-');
						consumeSymbol();
						break;
					case '0':
						char c = parseEscapedOctalCharacter();
						characterRangeRangeSet = createCharacterRange(firstSymbol, c);
						break;
					case 'u':
						c = parseEscapedUnicodeCharacter();
						characterRangeRangeSet = createCharacterRange(firstSymbol, c);
						consumeSymbol();
						break;
					case 'x':
						c = parseEscapedHexCharacter();
						characterRangeRangeSet = createCharacterRange(firstSymbol, c);
						consumeSymbol();
						break;
					case 'c':
						consumeSymbol();
						int charCode = (((currentSymbol - '@') % 128 + 128) % 128);
						c = (char) charCode;		
						consumeSymbol();
						characterRangeRangeSet =  createCharacterRange(firstSymbol, c);
						consumeSymbol();
						break;
					default:
						if ((currentSymbol >= 'A' && currentSymbol <= 'Z') || (currentSymbol >= 'a' && currentSymbol <= 'z') || (currentSymbol >= '0' && currentSymbol <= '9')) {
							throw new PatternSyntaxException("Illegal character range", transitionLabelString, index);
						} else {
							characterRangeRangeSet = createCharacterRange(firstSymbol, currentSymbol);
							consumeSymbol();
						}
						
					}

				} else if (currentSymbol == ']' || currentSymbol == '[') {
					characterRangeRangeSet = createCharacterRange(firstSymbol);
					characterRangeRangeSet.union(createCharacterRange('-'));
				} else {
					characterRangeRangeSet = createCharacterRange(firstSymbol, currentSymbol);
					consumeSymbol();
				}
			} else {
				throw new PatternSyntaxException("Illegal character range", transitionLabelString, index);
			}

		} else if (currentSymbol == '\\') {

			consumeSymbol();
			characterRangeRangeSet = createCharacterRange(firstSymbol);
			characterRangeRangeSet.union(parsePredefinedCharacterClass(currentSymbol));
		} else {
			characterRangeRangeSet = createCharacterRange(firstSymbol);
		}

		return characterRangeRangeSet;
	}

	private RangeSet createCharacterRange(char symbol) {
		int currentSymbolInt = (int) symbol;
		RangeSet characterRangeSet = new RangeSet(MIN_16UNICODE, MAX_16UNICODE);
		Range characterRange = characterRangeSet.createRange(currentSymbolInt, currentSymbolInt + 1);
		characterRangeSet.union(characterRange);
		return characterRangeSet;
	}

	private RangeSet createCharacterRange(char symbol1, char symbol2) {
		int currentSymbolInt1 = (int) symbol1;
		int currentSymbolInt2 = (int) symbol2;
		RangeSet characterRangeRangeSet = new RangeSet(MIN_16UNICODE, MAX_16UNICODE);
		try {
			Range characterRange = characterRangeRangeSet.createRange(currentSymbolInt1, currentSymbolInt2 + 1);
			characterRangeRangeSet.union(characterRange);
			return characterRangeRangeSet;
		} catch (IllegalArgumentException iae) {
			throw new PatternSyntaxException("Illegal character range", transitionLabelString, index);
		}

	}

	public static void main(String[] args) {
		TransitionLabelParserRecursive tpr = new TransitionLabelParserRecursive(args[0]);
		TransitionLabel parseTransitionLabel = tpr.parseTransitionLabel();
		System.out.println(parseTransitionLabel);
	}
}
