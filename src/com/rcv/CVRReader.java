/**
 * Created by Jonathan Moldover on 7/8/17
 * Copyright 2018 Bright Spots
 * Purpose: Helper class to read and parse an xls cast vote record file into
 * cast vote record objects.
 * Version: 1.0
 */

package com.rcv;

import com.sun.tools.javac.util.Pair;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class CVRReader {

  // container for all CastVoteRecords parsed from the input file
  public List<CastVoteRecord> castVoteRecords = new ArrayList<>();

  // purpose: parse the given file path into a CastVoteRecordList suitable for tabulation
  // Note: this is specific for the Maine example file we were provided
  // param: excelFilePath path to location of input cast vote record file
  // param: firstVoteColumnIndex the 0-based index where rankings begin for this ballot style
  // prarm: allowableRanks how many ranks are allowed for each cast vote record
  // param: candidateIDs list of all declared candidates' IDs
  // param: config an ElectionConfig object specifying rules for interpreting cvr file data
  public void parseCVRFile(
    String excelFilePath,
    int firstVoteColumnIndex,
    int allowableRanks,
    List<String>candidateIDs,
    ElectionConfig config
  ) {
    // contestSheet contains all the cvr data we will be parsing
    Sheet contestSheet = getFirstSheet(excelFilePath);
    if (contestSheet == null) {
      // TODO: this should probably throw
      Logger.log("invalid RCV format: could not obtain ballot data.");
      System.exit(1);
    }

    // validate header
    // Row iterator is used to iterate through a row of data from the sheet object
    Iterator<org.apache.poi.ss.usermodel.Row> iterator = contestSheet.iterator();
    // headerRow contains the first row
    org.apache.poi.ss.usermodel.Row headerRow = iterator.next();
    // we require at least one row
    if (headerRow == null || contestSheet.getLastRowNum() < 2) {
      Logger.log("invalid RCV format: not enough rows:%d", contestSheet.getLastRowNum());
      // TODO: this should probably throw
      System.exit(1);
    }

    // cvrFileName for generating cvrIDs
    String cvrFileName = new File(excelFilePath).getName();
    // cvrIndex for generating cvrIDs
    int cvrIndex = 1;

    // Iterate through all rows and create a CastVoteRecord for each row
    while (iterator.hasNext()) {
      // row object is used to iterate cvr file data for this cvr
      org.apache.poi.ss.usermodel.Row castVoteRecordRow = iterator.next();
      // unique ID for this castVoteRecord
      String castVoteRecordID =  String.format("%s(%d)",cvrFileName,cvrIndex++);
      // create object for this row
      ArrayList<Pair<Integer, String>> rankings = new ArrayList<>();
      // create an object to store CVR data for auditing
      ArrayList<String> fullCVRData = new ArrayList<>();

      // Iterate all expected cells in this row storing cvrData and rankings as we go
      // cellIndex ranges from 0 to the last expected rank column index
      for (int cellIndex = 0; cellIndex < firstVoteColumnIndex + allowableRanks; cellIndex++) {
        // cell object contains data the the current cell
        Cell cvrDataCell = castVoteRecordRow.getCell(cellIndex);
        if(cvrDataCell == null) {
          fullCVRData.add("empty cell");
        } else if(cvrDataCell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
          // parsed numeric data
          double doubleValue = cvrDataCell.getNumericCellValue();
          // convert back to a string (we only store String data from cvr files)
          fullCVRData.add(Double.toString(doubleValue));
        } else if (cvrDataCell.getCellType() == Cell.CELL_TYPE_STRING) {
          fullCVRData.add(cvrDataCell.getStringCellValue());
        } else {
          fullCVRData.add("unexpected data type");
        }

        // if we haven't reached a vote cell continue to the next cell
        if(cellIndex < firstVoteColumnIndex) {
          continue;
        }

        // rank for this cell
        int rank = cellIndex - firstVoteColumnIndex + 1;
        // candidate will be the candidate selected at this rank
        String candidate;
        if (cvrDataCell == null) {
          // empty cells are sometimes treated as undeclared write-ins (Portland / ES&S)
          if (config.treatBlankAsUWI()) {
            candidate = config.undeclaredWriteInLabel();
            Logger.log("Empty cell -- treating as UWI");
          } else {
            // just ignore this cell
            continue;
          }
        } else {
          if (cvrDataCell.getCellType() != Cell.CELL_TYPE_STRING) {
            Logger.log("unexpected cell type at ranking %d ballot %f", rank, castVoteRecordID);
            continue;
          }
          // TODO: how are overvotes parsed?  
          candidate = cvrDataCell.getStringCellValue().trim();

          if (candidate.equals(config.undervoteLabel())) {
            continue;
          } else if (candidate.equals(config.overvoteLabel())) {
            candidate = Tabulator.explicitOvervoteLabel;
          } else if (!candidateIDs.contains(candidate)) {
            if (!candidate.equals(config.undeclaredWriteInLabel())) {
              Logger.log("no match for candidate: %s", candidate);
            }
            candidate = config.undeclaredWriteInLabel();
          }
        }
        // create and add new ranking pair to the rankings list
        Pair<Integer, String> ranking = new Pair<>(rank, candidate);
        rankings.add(ranking);
      }
      // we now have all required data for the new CastVoteRecord object
      // create it and add to the list of all cvrs
      CastVoteRecord cvr = new CastVoteRecord(cvrFileName, castVoteRecordID, rankings, fullCVRData);
      castVoteRecords.add(cvr);
    }
    // parsing complete
  }

  // purpose: helper function to wrap file IO with error handling
  // param: excelFilePath path to file for parsing
  // file access: read
  // returns: the first xls sheet object in the file or null if there was a problem
  private static Sheet getFirstSheet(String excelFilePath) {
    // container for function results
    Sheet firstSheet = null;
    try {
      // inputStream is used to parse file data into memory
      FileInputStream inputStream = new FileInputStream(new File(excelFilePath));
      // excel workbook object allows access to sheet objects it contains
      Workbook workbook = new XSSFWorkbook(inputStream);
      firstSheet = workbook.getSheetAt(0);
      inputStream.close();
      workbook.close();
    } catch (IOException ex) {
      Logger.log("failed to process CVR file: %s, %s", excelFilePath, ex.getMessage());
    }
    return firstSheet;
  }

}
