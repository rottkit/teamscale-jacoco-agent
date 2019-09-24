/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2017 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.commandline;

import org.conqat.lib.commons.assertion.CCSMAssert;
import org.conqat.lib.commons.string.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to allow for multiple validations to occur.
 */
public class Validator {

	/** The found validation problems in the form of error messages for the user. */
	private final List<String> messages = new ArrayList<>();

	/** Runs the given validation routine. */
	public void ensure(ExceptionBasedValidation validation) {
		try {
			validation.validate();
		} catch (Exception | AssertionError e) {
			messages.add(e.getMessage());
		}
	}

	/**
	 * Interface for a validation routine that throws an exception when it fails.
	 */
	@FunctionalInterface
	public static interface ExceptionBasedValidation {

		/**
		 * Throws an {@link Exception} or {@link AssertionError} if the validation
		 * fails.
		 */
		public void validate() throws Exception, AssertionError;

	}

	/**
	 * Checks that the given condition is <code>true</code> or adds the given error
	 * message.
	 */
	public void isTrue(boolean condition, String message) {
		ensure(() -> CCSMAssert.isTrue(condition, message));
	}

	/**
	 * Checks that the given condition is <code>false</code> or adds the given error
	 * message.
	 */
	public void isFalse(boolean condition, String message) {
		ensure(() -> CCSMAssert.isFalse(condition, message));
	}

	/** Returns <code>true</code> if the validation succeeded. */
	public boolean isValid() {
		return messages.isEmpty();
	}

	/** Returns an error message with all validation problems that were found. */
	public String getErrorMessage() {
		return "- " + StringUtils.concat(messages, StringUtils.CR + "- ");
	}

}
