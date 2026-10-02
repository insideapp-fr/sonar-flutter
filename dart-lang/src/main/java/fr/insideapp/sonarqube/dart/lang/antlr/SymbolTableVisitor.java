/*
 * SonarQube Flutter Plugin - Enables analysis of Dart and Flutter projects into SonarQube.
 * Copyright © 2020 inside|app (contact@insideapp.fr)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.insideapp.sonarqube.dart.lang.antlr;

import fr.insideapp.sonarqube.dart.lang.antlr.generated.Dart2Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.symbol.NewSymbol;
import org.sonar.api.batch.sensor.symbol.NewSymbolTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

/**
 * Populates the SonarQube symbol table so the code viewer can highlight all
 * occurrences of an identifier. Linking is lexical and file-scoped: the first
 * occurrence of a name becomes the symbol, later occurrences become references.
 * This does not model scopes, so shadowed names are grouped together.
 */
public class SymbolTableVisitor implements ParseTreeItemVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymbolTableVisitor.class);

    @Override
    public void apply(ParseTree tree) {
        // handled entirely in fillContext
    }

    @Override
    public void fillContext(SensorContext context, AntlrContext antlrContext) {
        final InputFile file = antlrContext.getFile();
        if (file == null) {
            return;
        }
        final NewSymbolTable symbolTable = context.newSymbolTable().onFile(file);
        final Map<String, NewSymbol> symbols = new HashMap<>();

        for (final Token token : antlrContext.getTokens()) {
            if (token.getType() != Dart2Lexer.IDENTIFIER
                    || token.getStartIndex() > token.getStopIndex()) {
                continue;
            }
            final TextRange range = rangeOf(file, antlrContext, token);
            if (range == null) {
                continue;
            }
            try {
                final NewSymbol symbol = symbols.get(token.getText());
                if (symbol == null) {
                    symbols.put(token.getText(), symbolTable.newSymbol(range));
                } else {
                    symbol.newReference(range);
                }
            } catch (final Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(format("Unexpected error registering symbol %s on file %s", token.getText(), file.key()), e);
                }
            }
        }

        synchronized (SymbolTableVisitor.class) {
            try {
                symbolTable.save();
            } catch (final Exception e) {
                LOGGER.warn(format("Unexpected error saving symbol table on file %s", file.key()), e);
            }
        }
    }

    private static TextRange rangeOf(InputFile file, AntlrContext antlrContext, Token token) {
        final int[] end = antlrContext.getLineAndColumn(token.getStopIndex());
        if (end == null || end.length != 2) {
            return null;
        }
        try {
            return file.newRange(token.getLine(), token.getCharPositionInLine(), end[0], end[1] + 1);
        } catch (final Exception e) {
            return null;
        }
    }
}
