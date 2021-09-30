package util;

public class InterfaceSettings {

	public enum InputType {
		USER_INPUT,
		FILE_INPUT,
		COMMAND_LINE_INPUT
	}
	
	private final InputType inputType;
	public InputType getInputType() {
		return inputType;
	}

	private final boolean isVerbose;
	public boolean getIsVerbose() {
		return isVerbose;
	}

	private final boolean isDebug;
	public boolean getIsDebug() {
		return isDebug;
	}

	public InterfaceSettings(InputType inputType, boolean isVerbose, boolean isDebug) {
		this.inputType = inputType;
		this.isVerbose = isVerbose;
		this.isDebug = isDebug;
	}
}
