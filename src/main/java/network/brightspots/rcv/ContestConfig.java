/*
 * Universal RCV Tabulator
 * Copyright (c) 2017-2019 Bright Spots Developers.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Purpose:
 * Wrapper for RawContestConfig object. This class adds logic for looking up rule enum
 * names, candidate names, various configuration utilities, and cast vote record objects.
 */

package network.brightspots.rcv;

import static network.brightspots.rcv.Utils.isNullOrBlank;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import network.brightspots.rcv.RawContestConfig.CVRSource;
import network.brightspots.rcv.RawContestConfig.Candidate;
import network.brightspots.rcv.Tabulator.OvervoteRule;
import network.brightspots.rcv.Tabulator.TieBreakMode;
import network.brightspots.rcv.Tabulator.WinnerElectionMode;

class ContestConfig {
  // If any booleans are unspecified in config file, they should default to false no matter what
  static final String SUGGESTED_OUTPUT_DIRECTORY = "output";
  static final boolean SUGGESTED_TABULATE_BY_PRECINCT = false;
  static final boolean SUGGESTED_GENERATE_CDF_JSON = false;
  static final boolean SUGGESTED_CANDIDATE_EXCLUDED = false;
  static final boolean SUGGESTED_NON_INTEGER_WINNING_THRESHOLD = false;
  static final boolean SUGGESTED_HARE_QUOTA = false;
  static final boolean SUGGESTED_BATCH_ELIMINATION = false;
  static final boolean SUGGESTED_EXHAUST_ON_DUPLICATE_CANDIDATES = false;
  static final boolean SUGGESTED_TREAT_BLANK_AS_UNDECLARED_WRITE_IN = false;
  static final int SUGGESTED_NUMBER_OF_WINNERS = 1;
  static final int SUGGESTED_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC = 4;
  static final BigDecimal SUGGESTED_MINIMUM_VOTE_THRESHOLD = BigDecimal.ZERO;
  static final int SUGGESTED_MAX_SKIPPED_RANKS_ALLOWED = 1;
  static final WinnerElectionMode SUGGESTED_WINNER_ELECTION_MODE = WinnerElectionMode.STANDARD;
  private static final int MIN_COLUMN_INDEX = 1;
  private static final int MAX_COLUMN_INDEX = 1000;
  private static final int MIN_ROW_INDEX = 1;
  private static final int MAX_ROW_INDEX = 100000;
  private static final int MIN_MAX_RANKINGS_ALLOWED = 1;
  private static final int MIN_MAX_SKIPPED_RANKS_ALLOWED = 0;
  private static final int MIN_NUMBER_OF_WINNERS = 1;
  private static final int MIN_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC = 1;
  private static final int MAX_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC = 20;
  private static final int MIN_MINIMUM_VOTE_THRESHOLD = 0;
  private static final int MAX_MINIMUM_VOTE_THRESHOLD = 1000000;
  private static final int MIN_RANDOM_SEED = 0;
  private static final String CDF_PROVIDER = "CDF";
  private static final String JSON_EXTENSION = ".json";
  private static final String MAX_SKIPPED_RANKS_ALLOWED_UNLIMITED_OPTION = "unlimited";
  private static final String MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION = "max";
  static final String SUGGESTED_MAX_RANKINGS_ALLOWED = MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION;

  // underlying rawConfig object data
  final RawContestConfig rawConfig;
  // this is used if we have a permutation-based tie-break mode
  private final ArrayList<String> candidatePermutation = new ArrayList<>();
  private final Set<String> excludedCandidates = new HashSet<>();
  // path from which any relative paths should be resolved
  private final String sourceDirectory;
  // used for sequential multi-seat
  private final List<String> sequentialWinners = new LinkedList<>();
  // mapping from candidate code to full name
  private Map<String, String> candidateCodeToNameMap;
  // whether or not there are any validation errors
  private boolean isValid;

  // function: ContestConfig
  // purpose: create a new ContestConfig object
  // param: rawConfig underlying rawConfig object this object wraps
  // param: sourceDirectory folder to use for resolving relative paths
  private ContestConfig(RawContestConfig rawConfig, String sourceDirectory) {
    this.rawConfig = rawConfig;
    this.sourceDirectory = sourceDirectory;
  }

  // function: loadContestConfig
  // purpose: create ContestConfig from pre-populated rawConfig and default folder
  // returns: new ContestConfig object if checks pass otherwise null
  static ContestConfig loadContestConfig(RawContestConfig rawConfig, String sourceDirectory) {
    ContestConfig config = new ContestConfig(rawConfig, sourceDirectory);
    try {
      config.processCandidateData();
    } catch (Exception e) {
      Logger.log(Level.SEVERE, "Error processing candidate data:\n%s", e.toString());
      config = null;
    }
    return config;
  }

  // function: loadContestConfig
  // purpose: factory method to create ContestConfig from configPath
  // - create rawContestConfig from file - can fail for IO issues or invalid json
  // returns: new ContestConfig object if checks pass otherwise null
  static ContestConfig loadContestConfig(String configPath, boolean silentMode) {
    if (configPath == null) {
      Logger.log(Level.SEVERE, "No contest config path specified!");
      return null;
    }
    // config will hold the new ContestConfig if construction succeeds
    ContestConfig config = null;

    // rawConfig holds the basic contest config data parsed from json
    // this will be null if there is a problem loading it
    RawContestConfig rawConfig = JsonParser.readFromFile(configPath, RawContestConfig.class);
    if (rawConfig == null) {
      Logger.log(Level.SEVERE, "Failed to load contest config: %s", configPath);
    } else {
      if (!silentMode) {
        Logger.log(Level.INFO, "Successfully loaded contest config: %s", configPath);
      }
      // source folder will be the parent of configPath
      String parentFolder = new File(configPath).getParent();
      // if there is no parent folder use current working directory
      if (parentFolder == null) {
        parentFolder = System.getProperty("user.dir");
      }
      config = loadContestConfig(rawConfig, parentFolder);
    }
    return config;
  }

  static ContestConfig loadContestConfig(String configPath) {
    return loadContestConfig(configPath, false);
  }

  static boolean isCdf(CVRSource source) {
    return source.getProvider() != null && source.getProvider().toUpperCase().equals(CDF_PROVIDER)
        && source.getFilePath() != null && source.getFilePath().toLowerCase()
        .endsWith(JSON_EXTENSION);
  }

  // function: stringMatchesAnotherFieldValue(
  // purpose: Checks to make sure string value of one field doesn't match value of another field
  // param: string string to check
  // param: field field name of provided string
  // param: otherFieldValue string value of the other field
  // param: otherField name of the other field
  private static boolean stringMatchesAnotherFieldValue(
      String string, String field, String otherFieldValue, String otherField) {
    boolean match = false;
    if (!field.equals(otherField)) {
      if (!isNullOrBlank(otherFieldValue) && otherFieldValue.equalsIgnoreCase(string)) {
        match = true;
        Logger.log(
            Level.SEVERE,
            "\"%s\" can't be used as %s if it's also being used as %s!",
            string,
            field,
            otherField);
      }
    }
    return match;
  }

  // function: resolveConfigPath
  // purpose: given a path returns absolute path for use in File IO
  // param: path from this config file (cvr or output folder)
  // returns: resolved path
  String resolveConfigPath(String configPath) {
    // create File for IO operations
    File userFile = new File(configPath);
    // resolvedPath will be returned to caller
    String resolvedPath;
    if (userFile.isAbsolute()) {
      // path is already absolute so use as-is
      resolvedPath = userFile.getAbsolutePath();
    } else {
      // return sourceDirectory/configPath
      resolvedPath = Paths.get(sourceDirectory, configPath).toAbsolutePath().toString();
    }
    return resolvedPath;
  }

  RawContestConfig getRawConfig() {
    return rawConfig;
  }

  // function: validate
  // purpose: validate the correctness of the config data
  // returns any detected problems
  boolean validate() {
    Logger.log(Level.INFO, "Validating contest config...");
    isValid = true;
    validateTabulatorVersion();
    validateOutputSettings();
    validateCvrFileSources();
    validateCandidates();
    validateRules();
    if (isValid) {
      Logger.log(Level.INFO, "Contest config validation successful.");
    } else {
      Logger.log(
          Level.SEVERE,
          "Contest config validation failed! Please modify the contest config file and try again.\n"
              + "See config_file_documentation.txt for more details.");
    }

    return isValid;
  }

  private void invalidateAndLog(String message, String inputLocation) {
    isValid = false;
    message += inputLocation == null ? "!" : ": " + inputLocation;
    Logger.log(Level.SEVERE, message);
  }

  // Makes sure String input can be converted to an int, and checks that int against boundaries
  private void checkStringToIntWithBoundaries(String input, String inputName, Integer lowerBoundary,
      Integer upperBoundary, boolean isRequired) {
    checkStringToIntWithBoundaries(input, inputName, lowerBoundary, upperBoundary, isRequired,
        null);
  }

  // Makes sure String input can be converted to an int, and checks that int against boundaries
  private void checkStringToIntWithBoundaries(
      String input, String inputName, Integer lowerBoundary, Integer upperBoundary,
      boolean isRequired, String inputLocation) {
    String message = String.format("%s must be", inputName);
    if (lowerBoundary != null && upperBoundary != null) {
      if (lowerBoundary.equals(upperBoundary)) {
        message += String.format(" equal to %d", lowerBoundary);
      } else {
        message += String.format(" from %d to %d", lowerBoundary, upperBoundary);
      }
    } else if (lowerBoundary != null) {
      message += String.format(" at least %d", lowerBoundary);
    } else if (upperBoundary != null) {
      message += String.format(" no greater than %d", upperBoundary);
    } else {
      message += " provided";
    }
    if (isNullOrBlank(input)) {
      if (isRequired) {
        invalidateAndLog(message, inputLocation);
      }
    } else {
      try {
        int stringInt = Integer.parseInt(input);
        if ((lowerBoundary != null && stringInt < lowerBoundary)
            || (upperBoundary != null && stringInt > upperBoundary)) {
          if (!isRequired) {
            message += " if supplied";
          }
          invalidateAndLog(message, inputLocation);
        }
      } catch (NumberFormatException e) {
        message = String.format("%s must be an integer", inputName);
        if (!isRequired) {
          message += " if supplied";
        }
        invalidateAndLog(message, inputLocation);
      }
    }
  }

  // version validation and migration logic goes here
  // e.g. unsupported versions would fail or be migrated
  // in this release we support only the current app version
  private void validateTabulatorVersion() {
    if (isNullOrBlank(getTabulatorVersion())) {
      isValid = false;
      Logger.log(Level.SEVERE, "tabulatorVersion is required!");
    } else {
      if (!getTabulatorVersion().equals(Main.APP_VERSION)) {
        isValid = false;
        Logger.log(Level.SEVERE, "tabulatorVersion %s not supported!", getTabulatorVersion());
      }
    }
    if (!isValid) {
      Logger.log(Level.SEVERE, "tabulatorVersion must be set to %s!", Main.APP_VERSION);
    }
  }

  private void validateOutputSettings() {
    if (isNullOrBlank(getContestName())) {
      isValid = false;
      Logger.log(Level.SEVERE, "contestName is required!");
    }
  }

  private void validateCvrFileSources() {
    if (rawConfig.cvrFileSources == null || rawConfig.cvrFileSources.isEmpty()) {
      isValid = false;
      Logger.log(Level.SEVERE, "Contest config must contain at least 1 cast vote record file!");
    } else {
      HashSet<String> cvrFilePathSet = new HashSet<>();
      for (CVRSource source : rawConfig.cvrFileSources) {
        // perform checks on source input path
        if (isNullOrBlank(source.getFilePath())) {
          isValid = false;
          Logger.log(Level.SEVERE, "filePath is required for each cast vote record file!");
          continue;
        }

        // full path to CVR
        String cvrPath = resolveConfigPath(source.getFilePath());

        // look for duplicate paths
        if (cvrFilePathSet.contains(cvrPath)) {
          isValid = false;
          Logger.log(
              Level.SEVERE, "Duplicate cast vote record filePaths are not allowed: %s", cvrPath);
        } else {
          cvrFilePathSet.add(cvrPath);
        }

        // ensure file exists
        if (!new File(cvrPath).exists()) {
          isValid = false;
          Logger.log(Level.SEVERE, "Cast vote record file not found: %s", cvrPath);
        }

        // perform CDF checks
        if (isCdf(source)) {
          if (rawConfig.cvrFileSources.size() != 1) {
            isValid = false;
            Logger.log(Level.SEVERE, "CDF files must be tabulated individually.");
          }
          if (isTabulateByPrecinctEnabled()) {
            isValid = false;
            Logger.log(Level.SEVERE, "tabulateByPrecinct may not be used with CDF files.");
          }
        } else {
          // perform ES&S checks

          // ensure valid first vote column value
          checkStringToIntWithBoundaries(source.getFirstVoteColumnIndex(), "firstVoteColumnIndex",
              MIN_COLUMN_INDEX, MAX_COLUMN_INDEX, true, cvrPath);

          // ensure valid first vote row value
          checkStringToIntWithBoundaries(source.getFirstVoteRowIndex(), "firstVoteRowIndex",
              MIN_ROW_INDEX, MAX_ROW_INDEX, true, cvrPath);

          // ensure valid id column value
          checkStringToIntWithBoundaries(source.getIdColumnIndex(), "idColumnIndex",
              MIN_COLUMN_INDEX, MAX_COLUMN_INDEX, false, cvrPath);

          // ensure valid precinct column value
          checkStringToIntWithBoundaries(
              source.getPrecinctColumnIndex(), "precinctColumnIndex", MIN_COLUMN_INDEX,
              MAX_COLUMN_INDEX, false, cvrPath);

          if (isNullOrBlank(source.getPrecinctColumnIndex()) && isTabulateByPrecinctEnabled()) {
            isValid = false;
            Logger.log(
                Level.SEVERE,
                "precinctColumnIndex is required when tabulateByPrecinct is enabled: %s",
                cvrPath);
          }
        }
      }
    }
  }

  // function: stringAlreadyInUseElsewhere
  // purpose: Checks to make sure string isn't reserved or used by other fields
  // param: string string to check
  // param: field field name of provided string
  private boolean stringAlreadyInUseElsewhere(String string, String field) {
    boolean inUse = false;
    for (String reservedString : TallyTransfers.RESERVED_STRINGS) {
      if (string.equalsIgnoreCase(reservedString)) {
        inUse = true;
        Logger.log(Level.SEVERE, "\"%s\" is a reserved term and can't be used for %s!", string,
            field);
        break;
      }
    }
    if (!inUse) {
      inUse = stringMatchesAnotherFieldValue(string, field, getOvervoteLabel(), "overvoteLabel")
          || stringMatchesAnotherFieldValue(string, field, getUndervoteLabel(), "undervoteLabel")
          || stringMatchesAnotherFieldValue(string, field, getUndeclaredWriteInLabel(),
          "undeclaredWriteInLabel");
    }
    return inUse;
  }

  // function: candidateStringAlreadyInUseElsewhere
  // purpose: Takes a candidate name or code and checks for conflicts with other name/codes or other
  //   strings that are already being used in some other way.
  // param: candidateString is a candidate name or code
  // param: field is either "name" or "code"
  // param: candidateStringsSeen is a running set of names/codes we've already encountered
  private boolean candidateStringAlreadyInUseElsewhere(
      String candidateString, String field, Set<String> candidateStringsSeen) {
    boolean inUse;
    if (candidateStringsSeen.contains(candidateString)) {
      inUse = true;
      Logger.log(
          Level.SEVERE, "Duplicate candidate %ss are not allowed: %s", field, candidateString);
    } else {
      inUse = stringAlreadyInUseElsewhere(candidateString, "a candidate " + field);
    }
    return inUse;
  }

  private void validateCandidates() {
    Set<String> candidateNameSet = new HashSet<>();
    Set<String> candidateCodeSet = new HashSet<>();

    for (Candidate candidate : rawConfig.candidates) {
      if (isNullOrBlank(candidate.getName())) {
        isValid = false;
        Logger.log(Level.SEVERE, "Name is required for each candidate!");
      } else if (candidateStringAlreadyInUseElsewhere(
          candidate.getName(), "name", candidateNameSet)) {
        isValid = false;
      } else {
        candidateNameSet.add(candidate.getName());
      }

      if (!isNullOrBlank(candidate.getCode())) {
        if (candidateStringAlreadyInUseElsewhere(candidate.getCode(), "code", candidateCodeSet)) {
          isValid = false;
        } else {
          candidateCodeSet.add(candidate.getCode());
        }
      }
    }

    if (candidateCodeSet.size() > 0 && candidateCodeSet.size() != candidateNameSet.size()) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "If candidate codes are used, a unique code is required for each candidate!");
    }

    if (getNumDeclaredCandidates() < 1) {
      isValid = false;
      Logger.log(Level.SEVERE, "Contest config must contain at least 1 declared candidate!");
    } else if (getNumDeclaredCandidates() == excludedCandidates.size()) {
      isValid = false;
      Logger.log(Level.SEVERE, "Contest config must contain at least 1 non-excluded candidate!");
    }
  }

  private boolean isInt(String s) {
    boolean isInt = true;
    try {
      Integer.parseInt(s);
    } catch (NumberFormatException e) {
      isInt = false;
    }
    return isInt;
  }

  private void validateRules() {
    if (getTiebreakMode() == TieBreakMode.MODE_UNKNOWN) {
      isValid = false;
      Logger.log(Level.SEVERE, "Invalid tiebreakMode!");
    }

    if ((getTiebreakMode() == TieBreakMode.RANDOM
        || getTiebreakMode() == TieBreakMode.PREVIOUS_ROUND_COUNTS_THEN_RANDOM
        || getTiebreakMode() == TieBreakMode.GENERATE_PERMUTATION)
        && isNullOrBlank(getRandomSeedRaw())) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "When tiebreakMode involves a random element, randomSeed must be supplied.");
    }

    if (getOvervoteRule() == OvervoteRule.RULE_UNKNOWN) {
      isValid = false;
      Logger.log(Level.SEVERE, "Invalid overvoteRule!");
    } else if (!isNullOrBlank(getOvervoteLabel())
        && getOvervoteRule() != Tabulator.OvervoteRule.EXHAUST_IMMEDIATELY
        && getOvervoteRule() != Tabulator.OvervoteRule.ALWAYS_SKIP_TO_NEXT_RANK) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "When overvoteLabel is supplied, overvoteRule must be either exhaustImmediately "
              + "or alwaysSkipToNextRank!");
    }

    if (getWinnerElectionMode() == WinnerElectionMode.MODE_UNKNOWN) {
      isValid = false;
      Logger.log(Level.SEVERE, "Invalid winnerElectionMode!");
    }

    if (getMaxRankingsAllowed() == null) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "maxRankingsAllowed must either be \"%s\" or an integer!",
          MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION);
    } else if (getNumDeclaredCandidates() >= 1
        && getMaxRankingsAllowed() < MIN_MAX_RANKINGS_ALLOWED) {
      isValid = false;
      Logger.log(
          Level.SEVERE, "maxRankingsAllowed must be %d or higher!", MIN_MAX_RANKINGS_ALLOWED);
    }

    if (getMaxSkippedRanksAllowed() == null) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "maxSkippedRanksAllowed must either be \"%s\" or an integer!",
          MAX_SKIPPED_RANKS_ALLOWED_UNLIMITED_OPTION);
    } else if (getMaxSkippedRanksAllowed() < MIN_MAX_SKIPPED_RANKS_ALLOWED) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "maxSkippedRanksAllowed must be %d or higher!",
          MIN_MAX_SKIPPED_RANKS_ALLOWED);
    }

    checkStringToIntWithBoundaries(getNumberOfWinnersRaw(), "numberOfWinners",
        MIN_NUMBER_OF_WINNERS, getNumDeclaredCandidates() < 1 ? null : getNumDeclaredCandidates(),
        true);

    checkStringToIntWithBoundaries(getDecimalPlacesForVoteArithmeticRaw(),
        "decimalPlacesForVoteArithmetic",
        MIN_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC, MAX_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC, true);

    checkStringToIntWithBoundaries(getMinimumVoteThresholdRaw(), "minimumVoteThreshold",
        MIN_MINIMUM_VOTE_THRESHOLD, MAX_MINIMUM_VOTE_THRESHOLD, true);

    // If this is a multi-seat contest, we validate a couple extra parameters.
    if (!isNullOrBlank(getNumberOfWinnersRaw()) && isInt(getNumberOfWinnersRaw())
        && getNumberOfWinners() > 1) {
      if (isSingleSeatContinueUntilTwoCandidatesRemainEnabled()) {
        isValid = false;
        Logger.log(
            Level.SEVERE,
            "winnerElectionMode can't be singleSeatContinueUntilTwoCandidatesRemain in a "
                + "multi-seat contest!");
      }

      if (isBatchEliminationEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "batchElimination can't be true in a multi-seat contest!");
      }
    } else {
      if (isMultiSeatSequentialWinnerTakesAllEnabled()) {
        isValid = false;
        Logger.log(
            Level.SEVERE,
            "winnerElectionMode can't be multiSeatSequentialWinnerTakesAll in a single-seat " +
                "contest!");
      } else if (isMultiSeatBottomsUpEnabled()) {
        isValid = false;
        Logger.log(
            Level.SEVERE,
            "winnerElectionMode can't be multiSeatBottomsUp in a single-seat contest!");
      } else if (isMultiSeatAllowOnlyOneWinnerPerRoundEnabled()) {
        isValid = false;
        Logger.log(
            Level.SEVERE,
            "winnerElectionMode can't be multiSeatAllowOnlyOneWinnerPerRound in a single-seat "
                + "contest!");
      }

      if (isHareQuotaEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "hareQuota can only be true in a multi-seat contest!");
      }
    }

    if (isMultiSeatBottomsUpEnabled() && isBatchEliminationEnabled()) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "batchElimination can't be true when winnerElectionMode is multiSeatBottomsUp!");
    }

    checkStringToIntWithBoundaries(getRandomSeedRaw(), "randomSeed", MIN_RANDOM_SEED, null, false);

    if (!isNullOrBlank(getOvervoteLabel()) && stringAlreadyInUseElsewhere(getOvervoteLabel(),
        "overvoteLabel")) {
      isValid = false;
    }
    if (!isNullOrBlank(getUndervoteLabel()) && stringAlreadyInUseElsewhere(getUndervoteLabel(),
        "undervoteLabel")) {
      isValid = false;
    }
    if (!isNullOrBlank(getUndeclaredWriteInLabel())
        && stringAlreadyInUseElsewhere(getUndeclaredWriteInLabel(), "undeclaredWriteInLabel")) {
      isValid = false;
    }

    if (isTreatBlankAsUndeclaredWriteInEnabled() && isNullOrBlank(getUndeclaredWriteInLabel())) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "undeclaredWriteInLabel must be supplied if treatBlankAsUndeclaredWriteIn is true!");
    }
  }

  private String getNumberOfWinnersRaw() {
    return rawConfig.rules.numberOfWinners;
  }

  // function: getNumberWinners
  // purpose: how many winners for this contest
  // returns: number of winners
  Integer getNumberOfWinners() {
    return Integer.parseInt(rawConfig.rules.numberOfWinners);
  }

  void setNumberOfWinners(int numberOfWinners) {
    rawConfig.rules.numberOfWinners = Integer.toString(numberOfWinners);
  }

  List<String> getSequentialWinners() {
    return sequentialWinners;
  }

  void addSequentialWinner(String winner) {
    sequentialWinners.add(winner);
  }

  private String getDecimalPlacesForVoteArithmeticRaw() {
    return rawConfig.rules.decimalPlacesForVoteArithmetic;
  }

  // function: getDecimalPlacesForVoteArithmetic
  // purpose: how many places to round votes to after performing fractional vote transfers
  // returns: number of places to round to
  Integer getDecimalPlacesForVoteArithmetic() {
    return Integer.parseInt(rawConfig.rules.decimalPlacesForVoteArithmetic);
  }

  WinnerElectionMode getWinnerElectionMode() {
    WinnerElectionMode mode = WinnerElectionMode.getByLabel(rawConfig.rules.winnerElectionMode);
    return mode == null ? WinnerElectionMode.MODE_UNKNOWN : mode;
  }

  boolean isSingleSeatContinueUntilTwoCandidatesRemainEnabled() {
    return getWinnerElectionMode()
        == WinnerElectionMode.SINGLE_SEAT_CONTINUE_UNTIL_TWO_CANDIDATES_REMAIN;
  }

  boolean isMultiSeatAllowOnlyOneWinnerPerRoundEnabled() {
    return getWinnerElectionMode() == WinnerElectionMode.MULTI_SEAT_ALLOW_ONLY_ONE_WINNER_PER_ROUND;
  }

  boolean isMultiSeatBottomsUpEnabled() {
    return getWinnerElectionMode() == WinnerElectionMode.MULTI_SEAT_BOTTOMS_UP;
  }

  boolean isMultiSeatSequentialWinnerTakesAllEnabled() {
    return getWinnerElectionMode() == WinnerElectionMode.MULTI_SEAT_SEQUENTIAL_WINNER_TAKES_ALL;
  }

  boolean isNonIntegerWinningThresholdEnabled() {
    return rawConfig.rules.nonIntegerWinningThreshold;
  }

  boolean isHareQuotaEnabled() {
    return rawConfig.rules.hareQuota;
  }

  // function: divide
  // purpose: perform a division operation according to the config settings
  // param: dividend is the numerator in the division operation
  // param: divisor is the denominator in the division operation
  // returns: the quotient
  BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
    return dividend.divide(divisor, getDecimalPlacesForVoteArithmetic(), RoundingMode.DOWN);
  }

  BigDecimal multiply(BigDecimal multiplier, BigDecimal multiplicand) {
    return multiplier
        .multiply(multiplicand)
        .setScale(getDecimalPlacesForVoteArithmetic(), RoundingMode.DOWN);
  }

  // function: getOutputDirectoryRaw
  // purpose: getter for outputDirectory
  // returns: raw string from config or falls back to user folder if none is set
  String getOutputDirectoryRaw() {
    // outputDirectory is where output files should be written
    return rawConfig.outputSettings.outputDirectory;
  }

  // function: getOutputDirectory
  // purpose: get the directory location where output files should be written
  // returns: path to directory where output files should be written
  String getOutputDirectory() {
    return resolveConfigPath(getOutputDirectoryRaw());
  }

  private String getTabulatorVersion() {
    return rawConfig.tabulatorVersion;
  }

  // function: getContestName
  // purpose: getter for contestName
  // returns: contest name
  String getContestName() {
    return rawConfig.outputSettings.contestName;
  }

  // function: getContestJurisdiction
  // purpose: getter for contestJurisdiction
  // returns: contest jurisdiction name
  String getContestJurisdiction() {
    return rawConfig.outputSettings.contestJurisdiction;
  }

  // function: getContestOffice
  // purpose: getter for contestOffice
  // returns: contest office name
  String getContestOffice() {
    return rawConfig.outputSettings.contestOffice;
  }

  // function: getContestDate
  // purpose: getter for contestDate
  // returns: contest date
  String getContestDate() {
    return rawConfig.outputSettings.contestDate;
  }

  // function: isTabulateByPrecinctEnabled
  // purpose: getter for tabulateByPrecinct
  // returns: true if and only if we should tabulate by precinct
  boolean isTabulateByPrecinctEnabled() {
    return rawConfig.outputSettings.tabulateByPrecinct;
  }

  boolean isGenerateCdfJsonEnabled() {
    return rawConfig.outputSettings.generateCdfJson;
  }

  // Converts a String to an Integer and also allows for an additional option as valid input
  private Integer stringToIntWithOption(String rawInput, String optionFlag, Integer optionResult) {
    Integer intValue;
    if (isNullOrBlank(rawInput)) {
      intValue = null;
    } else if (rawInput.equalsIgnoreCase(optionFlag)) {
      intValue = optionResult;
    } else {
      try {
        intValue = Integer.parseInt(rawInput);
      } catch (NumberFormatException e) {
        intValue = null;
      }
    }
    return intValue;
  }

  // function: getMaxRankingsAllowed
  // purpose: getter for maxRankingsAllowed
  // returns: max rankings allowed
  Integer getMaxRankingsAllowed() {
    return stringToIntWithOption(
        rawConfig.rules.maxRankingsAllowed,
        MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION,
        getNumDeclaredCandidates());
  }

  // function: isBatchEliminationEnabled
  // purpose: getter for batchElimination
  // returns: true if and only if we should use batch elimination
  boolean isBatchEliminationEnabled() {
    return rawConfig.rules.batchElimination;
  }

  // function: numDeclaredCandidates
  // purpose: calculate the number of declared candidates from the contest configuration
  // returns: the number of declared candidates from the contest configuration
  int getNumDeclaredCandidates() {
    // num will contain the resulting number of candidates
    int num = getCandidateCodeList().size();
    if (!isNullOrBlank(getUndeclaredWriteInLabel())
        && getCandidateCodeList().contains(getUndeclaredWriteInLabel())) {
      num--;
    }
    return num;
  }

  // function: numCandidates
  // purpose: return number of candidates including UWIs as a candidate if they are in use
  // num will contain the resulting number of candidates
  int getNumCandidates() {
    return getCandidateCodeList().size();
  }

  boolean candidateIsExcluded(String candidate) {
    return excludedCandidates.contains(candidate);
  }

  // function: getOvervoteRule
  // purpose: return overvote rule enum to use
  // returns: overvote rule to use for this config
  OvervoteRule getOvervoteRule() {
    OvervoteRule rule = OvervoteRule.getByLabel(rawConfig.rules.overvoteRule);
    return rule == null ? OvervoteRule.RULE_UNKNOWN : rule;
  }

  private String getMinimumVoteThresholdRaw() {
    return rawConfig.rules.minimumVoteThreshold;
  }

  // function: getMinimumVoteThreshold
  // purpose: getter for minimumVoteThreshold rule
  // returns: minimum vote threshold to use or default value if it's not specified
  BigDecimal getMinimumVoteThreshold() {
    return new BigDecimal(rawConfig.rules.minimumVoteThreshold);
  }

  // function: getMaxSkippedRanksAllowed
  // purpose: getter for maxSkippedRanksAllowed rule
  // returns: max skipped ranks allowed in this config
  Integer getMaxSkippedRanksAllowed() {
    return stringToIntWithOption(
        rawConfig.rules.maxSkippedRanksAllowed,
        MAX_SKIPPED_RANKS_ALLOWED_UNLIMITED_OPTION,
        Integer.MAX_VALUE);
  }

  // function: getUndeclaredWriteInLabel
  // purpose: getter for UWI label
  // returns: UWI label for this config
  String getUndeclaredWriteInLabel() {
    return rawConfig.rules.undeclaredWriteInLabel;
  }

  // function: getOvervoteLabel
  // purpose: getter for overvote label rule
  // returns: overvote label for this config
  String getOvervoteLabel() {
    return rawConfig.rules.overvoteLabel;
  }

  // function: getUndervoteLabel
  // purpose: getter for undervote label
  // returns: undervote label for this config
  String getUndervoteLabel() {
    return rawConfig.rules.undervoteLabel;
  }

  // function: getTiebreakMode
  // purpose: return tiebreak mode to use
  // returns: tiebreak mode to use for this config
  TieBreakMode getTiebreakMode() {
    TieBreakMode mode = TieBreakMode.getByLabel(rawConfig.rules.tiebreakMode);
    return mode == null ? TieBreakMode.MODE_UNKNOWN : mode;
  }

  private String getRandomSeedRaw() {
    return rawConfig.rules.randomSeed;
  }

  Integer getRandomSeed() {
    return Integer.parseInt(rawConfig.rules.randomSeed);
  }

  // function: isTreatBlankAsUndeclaredWriteInEnabled
  // purpose: getter for treatBlankAsUndeclaredWriteIn rule
  // returns: true if we are to treat blank cell as UWI
  boolean isTreatBlankAsUndeclaredWriteInEnabled() {
    return rawConfig.rules.treatBlankAsUndeclaredWriteIn;
  }

  // function: isExhaustOnDuplicateCandidateEnabled
  // purpose: getter for exhaustOnDuplicateCandidate rule
  // returns: true if tabulation should exhaust ballot when encountering a duplicate candidate
  boolean isExhaustOnDuplicateCandidateEnabled() {
    return rawConfig.rules.exhaustOnDuplicateCandidate;
  }

  // function: getCandidateCodeList
  // purpose: return list of candidate codes for this config
  // returns: return list of candidate codes for this config
  Set<String> getCandidateCodeList() {
    return candidateCodeToNameMap.keySet();
  }

  // function: getNameForCandidateCode
  // purpose: look up full candidate name given a candidate code
  // param: code the code of the candidate whose name we want to look up
  // returns: the full candidate name for the given candidate code
  String getNameForCandidateCode(String code) {
    return candidateCodeToNameMap.get(code);
  }

  // function: getCandidatePermutation
  // purpose: getter for ordered list of candidates for tie-breaking
  // returns: ordered list of candidates
  ArrayList<String> getCandidatePermutation() {
    return candidatePermutation;
  }

  void setCandidateExclusionStatus(String candidateCode, boolean excluded) {
    if (excluded) {
      excludedCandidates.add(candidateCode);
    } else {
      excludedCandidates.remove(candidateCode);
    }
  }

  // function: processCandidateData
  // purpose: perform pre-processing on candidates:
  // 1) if there are any CDF input sources extract candidates names from them
  // 2) build map of candidate ID to candidate name
  // 3) generate tie-break ordering if needed
  private void processCandidateData() {
    candidateCodeToNameMap = new HashMap<>();

    for (RawContestConfig.CVRSource source : rawConfig.cvrFileSources) {
      // for any CDF sources extract candidate names
      if (isCdf(source)) {
        // cvrPath is the resolved path to this source
        String cvrPath = resolveConfigPath(source.getFilePath());
        CommonDataFormatReader reader = new CommonDataFormatReader(cvrPath, this);
        candidateCodeToNameMap = reader.getCandidates();
        candidatePermutation.addAll(candidateCodeToNameMap.keySet());
      }
    }

    if (rawConfig.candidates != null) {
      for (RawContestConfig.Candidate candidate : rawConfig.candidates) {
        String code = candidate.getCode();
        String name = candidate.getName();
        if (isNullOrBlank(code)) {
          code = name;
        }

        // duplicate names or codes get caught in validation
        candidateCodeToNameMap.put(code, name);
        candidatePermutation.add(code);
        if (candidate.isExcluded()) {
          excludedCandidates.add(code);
        }
      }
    }

    if (getTiebreakMode() == TieBreakMode.GENERATE_PERMUTATION) {
      // It's not valid to have a null random seed with this tie-break mode; the validation will
      // catch that and report a helpful error. Validation also hits this code path, though, so we
      // need to prevent a NullPointerException here.
      if (!isNullOrBlank(getRandomSeedRaw()) && isInt(getRandomSeedRaw())) {
        Collections.shuffle(candidatePermutation, new Random(getRandomSeed()));
      }
    }

    String uwiLabel = getUndeclaredWriteInLabel();
    if (!isNullOrBlank(uwiLabel)) {
      candidateCodeToNameMap.put(uwiLabel, uwiLabel);
    }
  }
}
