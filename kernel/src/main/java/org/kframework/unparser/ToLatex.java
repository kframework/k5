// Copyright (c) 2019 K Team. All Rights Reserved.
package org.kframework.unparser;

import org.kframework.attributes.Att;
import org.kframework.definition.Module;
import org.kframework.definition.NonTerminal;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.definition.Terminal;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KAs;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.StringUtil;

import java.io.IOException;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

import scala.collection.JavaConverters;

/**
 * Writes a KAST term to the LaTeX format.
 */
public class ToLatex {

    public static byte[] apply(K k) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            apply(new DataOutputStream(out), k);
            return out.toByteArray();
        } catch (IOException e) {
            throw KEMException.criticalError("Could not write K term to LaTeX", e, k);
        }
    }

    public static byte[] makePrelude(Module mod) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            makePrelude(new DataOutputStream(out), mod);
            return out.toByteArray();
        } catch (IOException e) {
            throw KEMException.criticalError("Could not write LaTeX prelude for K module " + mod.name(), e);
        }
    }

    private static String[] asciiReadableEncodingLatexCalc() {
        String[] latexEncoder = Arrays.copyOf(StringUtil.asciiReadableEncodingDefault, StringUtil.asciiReadableEncodingDefault.length);
        latexEncoder[0x30] = "Zero";
        latexEncoder[0x31] = "I";
        latexEncoder[0x32] = "II";
        latexEncoder[0x33] = "III";
        latexEncoder[0x34] = "IV";
        latexEncoder[0x35] = "V";
        latexEncoder[0x36] = "VI";
        latexEncoder[0x37] = "VII";
        latexEncoder[0x38] = "VIII";
        latexEncoder[0x39] = "IX";
        latexEncoder[0x7a] = "ActZ";
        return latexEncoder;
    }

    private static String escapeLatex(String input) {
        return input.replace("&", "\\&")
                    .replace("%", "\\%")
                    .replace("$", "\\$")
                    .replace("#", "\\#")
                    .replace("_", "\\_")
                    .replace("{", "\\{")
                    .replace("}", "\\}");
    }

    public static final Pattern identChar = Pattern.compile("[A-Za-y]");
    public static String[] asciiReadableEncodingLatex = asciiReadableEncodingLatexCalc();

    public static String latexedKLabel(String orig) {
        StringBuilder buffer = new StringBuilder();
        StringUtil.encodeStringToAlphanumeric(buffer, orig, asciiReadableEncodingLatex, identChar, "z");
        return "klabel" + buffer.toString();
    }

    private static void writeString(DataOutputStream out, String str) throws IOException {
        out.write(str.getBytes(StandardCharsets.UTF_8));
    }

    public static void apply(DataOutputStream out, Att att) throws IOException {
        writeString(out, ("\\outerAtt{" + att.toString() + "}"));
    }

    public static void apply(DataOutputStream out, K k) throws IOException {
        if (k instanceof KToken) {
            KToken tok = (KToken) k;

            writeString(out, ("\\texttt{ " + tok.s() + " }"));

        } else if (k instanceof KApply) {
            KApply app = (KApply) k;

            writeString(out, ("\\" + latexedKLabel(app.klabel().name())));

            for (K item : app.klist().asIterable()) {
                writeString(out, "{");
                apply(out, item);
                writeString(out, "}");
            }

        } else if (k instanceof KSequence) {
            KSequence kseq = (KSequence) k;

            writeString(out, "\\kseq{");

            for (K item : kseq.asIterable()) {
                apply(out, item);
                writeString(out, "}{\\kseq{");
            }

            writeString(out, "}{\\dotk{}}");

        } else if (k instanceof KVariable) {
            KVariable var = (KVariable) k;

            Optional<String> origName = var.att().getOptional("originalName");
            if (origName.isPresent()) {
                writeString(out, origName.get());
            } else {
                writeString(out, var.name());
            }

        } else if (k instanceof KRewrite) {
            KRewrite rew = (KRewrite) k;

            writeString(out, "\\krewrites{");
            apply(out, rew.left());
            writeString(out, "}{");
            apply(out, rew.right());
            writeString(out, "}{");
            apply(out, rew.att());
            writeString(out, "}");

        } else if (k instanceof KAs) {
            KAs alias = (KAs) k;

            writeString(out, "\\kas{");
            apply(out, alias.pattern());
            writeString(out, "}{");
            apply(out, alias.alias());
            writeString(out, "}{");
            apply(out, alias.att());
            writeString(out, "}");

        } else if (k instanceof InjectedKLabel) {
            InjectedKLabel inj = (InjectedKLabel) k;

            writeString(out, "\\injectedklabel{");
            writeString(out, inj.klabel().name());
            writeString(out, "}");

        } else {
            throw KEMException.criticalError("Unimplemented for LaTeX serialization: ", k);
        }
    }

    public static void makePrelude(DataOutputStream out, Module mod) throws IOException {
        for (Production p: JavaConverters.setAsJavaSet(mod.productions())) {
            if (! p.isSyntacticSubsort() && ! p.klabel().isEmpty() && ! p.att().contains("unparseAvoid")) {
                String arity      = Integer.toString(p.arity());
                String command    = latexedKLabel(p.klabel().get().name());
                String format     = "";
                String identifier = p.klabel().get().name(); // Include source info?
                if (p.att().contains("latex")) {
                    format = p.att().get("latex");
                } else {
                    int nonTerminal = 1;
                    for (ProductionItem pItem: JavaConverters.seqAsJavaList(p.items())) {
                        if (pItem instanceof Terminal) {
                            format += "\\mathtt{" + escapeLatex(((Terminal) pItem).value()) + "}";
                        }
                        if (pItem instanceof NonTerminal) {
                            format += "{#" + Integer.toString(nonTerminal) + "}";
                            nonTerminal++;
                        }
                    }
                }
                String newcommand = String.format("%-" + 120 + "s", "\n\\newcommand{\\" + command + "}[" + arity + "]{" + format + "}");
                writeString(out, newcommand + " % " + identifier);
            }
        }
    }
}
