package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Created by jdeyoung on 6/4/15.
 */
public class Warning {

  final String warningString;
  final Severity severity;

  public Warning(
      final String warningString, final Severity severity) {
    this.warningString = warningString;
    this.severity = severity;
  }

  public static Warning create(final String warningString, final Severity severity) {
    return new Warning(warningString, severity);
  }

  public static ImmutableSet<Severity> extractSeverity(final Iterable<Warning> warnings) {
    Set<Severity> severities = Sets.newHashSet();
    for (Warning w : warnings) {
      severities.add(w.severity);
    }
    return ImmutableSet.copyOf(severities);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Warning warning = (Warning) o;

    if (warningString != null ? !warningString.equals(warning.warningString)
                              : warning.warningString != null) {
      return false;
    }
    return severity == warning.severity;

  }

  public String warningString() {
    return warningString;
  }

  public Severity severity() {
    return severity;
  }

  @Override
  public int hashCode() {
    int result = warningString != null ? warningString.hashCode() : 0;
    result = 31 * result + (severity != null ? severity.hashCode() : 0);
    return result;
  }

  public enum Severity {
    MAJOR("major", ".major {\n"
        + "box-sizing: border-box;\n"
        + "margin: 2px;\n"
        + "border-color: red;\n"
        + "background-color: red;\n"
        + "border-width: 2px;\n"
        + "border-style: solid;\n"
        + "visibility: inherit;\n"
        + "font-weight: bold;\n"
        + "}\n"),
    MINOR("minor", ".minor {\n"
        + "box-sizing: border-box;\n"
        + "margin: 2px;\n"
        + "border-color: orange;\n"
        + "background-color: orange;\n"
        + "border-width: 2px;\n"
        + "border-style: solid;\n"
        + "visibility: inherit;\n"
        + "font-weight: bold;\n"
        + "}\n");

    final String CSSClassName;
    final String CSS;

    Severity(final String cssClassName, final String css) {
      CSSClassName = cssClassName;
      CSS = css;
    }

    public String CSSClassName() {
      return CSSClassName;
    }

    public String CSS() {
      return CSS;
    }
  }
}