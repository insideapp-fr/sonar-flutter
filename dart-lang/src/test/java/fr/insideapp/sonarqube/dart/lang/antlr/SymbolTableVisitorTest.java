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

import fr.insideapp.sonarqube.dart.lang.Dart;
import org.junit.Test;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SymbolTableVisitorTest {

    @Test
    public void linksRepeatedIdentifiers() throws Exception {
        final String code = "int counter = 0;\nvoid inc() { counter = counter + 1; }\n";
        final Path baseDir = Files.createTempDirectory("dartsym");
        final Path source = baseDir.resolve("sample.dart");
        Files.write(source, code.getBytes(StandardCharsets.UTF_8));

        final DefaultInputFile inputFile = TestInputFileBuilder
                .create("moduleKey", baseDir.toFile(), source.toFile())
                .setLanguage(Dart.KEY)
                .setCharset(StandardCharsets.UTF_8)
                .setContents(code)
                .build();

        final SensorContextTester context = SensorContextTester.create(baseDir);
        context.fileSystem().add(inputFile);

        final AntlrContext antlrContext = AntlrContext.fromInputFile(inputFile, StandardCharsets.UTF_8);
        new CustomTreeVisitor(new SymbolTableVisitor()).fillContext(context, antlrContext);

        // 'counter' is declared on line 1 col 4; its occurrences on line 2 must be references
        final java.util.Collection<org.sonar.api.batch.fs.TextRange> refs =
                context.referencesForSymbolAt(inputFile.key(), 1, 4);
        assertThat(refs).as("counter should have references").isNotNull();
        assertThat(refs).hasSize(2);
    }
}
