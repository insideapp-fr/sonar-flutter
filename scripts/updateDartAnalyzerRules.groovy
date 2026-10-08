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
/*
 Update rules.json and profile-dartanalyzer.xml from Dart Linter rules website.
 Severity is assigned based on category and rule naming
 */

import groovy.xml.MarkupBuilder
@Grab(group = 'org.codehaus.groovy.modules.http-builder',
        module = 'http-builder', version = '0.7')

import groovyx.net.http.*
@Grab(group = 'org.codehaus.groovy.modules.http-builder',
        module = 'http-builder', version = '0.7')

import groovyx.net.http.*
@Grab(group = 'org.codehaus.groovy.modules.http-builder',
        module = 'http-builder', version = '0.7')

import groovy.xml.*

import commons.RuleUpdater
import commons.ConsoleString
import commons.Rule

String.mixin(ConsoleString)

/** Parse rule HTML description
 * @param ruleKey Rule key
 * @return HTML content as String
 */
def parseRuleDescription(String ruleKey) {

    try {

    def http = new HTTPBuilder('https://dart.dev/tools/linter-rules/' + ruleKey)
    def html = http.get([:])

    def root = html."**".find { it.name() == "ARTICLE" }

    def description = ""
    def started = false
    root."**".findAll { it.name() == "DIV" || it.name() == "P" || it.name() == "H2" || it.name() == "CODE" }.each {tag -> 
        switch (tag.name()) {
            
            case 'DIV':
                if (tag.text().startsWith("Details")) {
                    started = true
                }
                break
            case 'H2':
                if (tag.text().startsWith("Usage")) {
                    started = false
                }
                break
            case 'P':
                if (started) {
                    description += XmlUtil.serialize(tag).replace('<?xml version="1.0" encoding="UTF-8"?>', '')
                }
                break
            case 'CODE':
                if (started) {
                    description += XmlUtil.serialize(tag).replace('<?xml version="1.0" encoding="UTF-8"?>', '')
                }
                break
        }
    }


    return '<SECTION>' + description + '</SECTION>'

    } catch (Exception e) {
        return null
    }

}

/**
 * Parse rules from the lint website
 * @param url
 * @return
 */
def parseRules(url) {

    def result = []

    def http = new HTTPBuilder(url)
    def html = http.get([:])

    def root = html."**".find { it.name() == "ARTICLE" }

    def currentKey = ''
    def currentActive = true


    def ruleIds = []
    root."**".findAll { it.name() == "P" }.each { pTag ->

        currentActive = !pTag.text().contains("(Removed)")
  
        pTag."**".findAll { it.name() == "A" }.each { aTag ->
            aTag."**".findAll { it.name() == "CODE" }.each { codeTag ->
                if (!codeTag.text().contains(' ')) {
                    currentKey = codeTag.text()
                    def description = parseRuleDescription(currentKey)
                    if (description != null) {
                        def rule = new Rule(
                                currentKey,
                                description,
                                null, // no severity
                                null, // no type
                                currentKey.replace("_", " ").capitalize(),
                                null, // no debt
                                currentActive
                            )
                        result.add(rule)
                        currentActive = true
                        currentKey = ''
                    }
                }
            }
        }
    }
    
    result
}

/**
 * Parse rules from the diagnostic messages website
 * @param url
 * @return
 */

def parseRulesFromDiagnostic(url) {
    def result = []

    def http = new HTTPBuilder(url)
    def html = http.get([:])

    def root = html."**".find { it.name() == "ARTICLE" }

    def currentKey = ''
    def currentDescription = ''
    def descriptionOver = false

    root."**".findAll { it.name() == "H3" || it.name() == "H4" || it.name() == "P" }.each { tag ->
        switch (tag.name()) {
            case 'H3':
                currentKey = tag.text()
                break
            case 'H4':
                if (tag.text() == "Description") {
                    //We might have several lines of entries
                    descriptionOver = false
                    currentDescription = ""
                } else if (tag.text() == "Examples") {
                    descriptionOver = true
                    //When reaching examples, we consider the description over
                    //todo but we might want to provider nicer description with examples later !
                    def rule = new Rule(
                            currentKey,
                            currentDescription += "\n @see https://dart.dev/tools/diagnostic-messages#$currentKey",
                            null, // no severity
                            null, // no type
                            currentKey.replace("_", " ").capitalize(),
                            null, // no debt
                            true // active
                    )
                    result.add(rule)
                }
                break
            case 'P':
                if (!descriptionOver) {
                    currentDescription += tag.text()
                }
                break
        }
    }
    result
}

/**
 * Write default analysis_options.yaml
 * Only lint rules belong to the linter section: diagnostic messages are always reported by the analyzer
 * and are rejected as unknown lints. Removed lints (inactive) and duplicates are skipped.
 * @param lintRules
 * @param file
 * @return
 */
def writeAnalysisOptions(lintRules, file) {
    text = "# Generated by scripts/updateDartAnalyzerRules.groovy\n"
    text += "# Every known lint is enabled: the SonarQube quality profile decides which issues are kept.\n"
    text += "analyzer:\n"
    text += "  errors:\n"
    text += "    # This file is used with any Dart SDK version, so some lints are always deprecated, removed,\n"
    text += "    # unknown or incompatible with each other for the SDK in use: do not report this file itself.\n"
    text += "    deprecated_lint: ignore\n"
    text += "    incompatible_lint: ignore\n"
    text += "    removed_lint: ignore\n"
    text += "    undefined_lint: ignore\n"
    text += "linter:\n"
    text += "  rules:\n"
    lintRules.findAll { r -> r.active }.collect { r -> r.key }.unique().forEach { key ->
        text += "    - ${key}\n"
    }
    file.text = text
}


def updater = new RuleUpdater('dart-lang/src/main/resources/dartanalyzer/rules.json', {

    def lintRules = parseRules('https://dart.dev/tools/linter-rules')
    writeAnalysisOptions(lintRules, new File('dart-lang/src/main/resources/dartanalyzer/analysis_options.yaml'))

    def rules = new ArrayList(lintRules)
    rules.addAll(parseRulesFromDiagnostic('https://dart.dev/tools/diagnostic-messages'))

    return rules
})

updater.update(Integer.parseInt(project.properties['scripts.max-manual']))