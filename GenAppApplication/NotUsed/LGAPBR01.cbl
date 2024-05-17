      ******************************************************************
      *                                                                *
      * LICENSED MATERIALS - PROPERTY OF IBM                           *
      *                                                                *
      * "RESTRICTED MATERIALS OF IBM"                                  *
      *                                                                *
      * CB12                                                           *
      *                                                                *
      * (C) COPYRIGHT IBM CORP. 2011, 2013 ALL RIGHTS RESERVED         *
      *                                                                *
      * US GOVERNMENT USERS RESTRICTED RIGHTS - USE, DUPLICATION,      *
      * OR DISCLOSURE RESTRICTED BY GSA ADP SCHEDULE                   *
      * CONTRACT WITH IBM CORPORATION                                  *
      *                                                                *
      *                                                                *
      *                  Perform Business Rule Processing              *
      *                                                                *
      *   To perform a call to the ODM Business Rule Execution Server  *
      *   if an Endowment policy is being added                        *
      *                                                                *
      * INPUT:                                                         *
      *   commarea of sufficient length to contain policy details plus:*
      *   - Request id = "nnAEND" where nn is the version number       *
      *                 where nn is the version number                 *
      *   - Customer Number                                            *
      *   - full policy details (as appropriate)                       *
      *                                                                *
      * OUTPUT:                                                        *
      *   return commarea containing:                                  *
      *   - return code                                                *
      *   - Policy details                                             *
      *   - Abend code if No commarea received                         *
      *                                                                *
      * RETURN CODES:                                                  *
      *  00 - Successful                                               *
      *  98 - Commarea received is not large enough                    *
      *  99 - Request not recognised or supported                      *
      *                                                                *
      * RETURN CODES:                                                  *
      *  LGCA - No commarea received (and is required)                 *
      *                                                                *
      * REQUIREMENTS/DEPENDENCIES:                                     *
      *                                                                *
      * NOTES:                                                         *
      *                                                                *
      * CHANGE HISTORY:                                                *
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. LGAPBR01.
       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
      *
       DATA DIVISION.

       WORKING-STORAGE SECTION.

      *----------------------------------------------------------------*
      * Common defintions                                              *
      *----------------------------------------------------------------*
      * Run time (debug) infomation for this invocation
        01  WS-HEADER.
           03 WS-EYECATCHER            PIC X(16)
                                        VALUE 'LGAPBR01------WS'.
           03 WS-TRANSID               PIC X(4).
           03 WS-TERMID                PIC X(4).
           03 WS-TASKNUM               PIC 9(7).
           03 WS-FILLER                PIC X.
           03 WS-ADDR-DFHCOMMAREA      USAGE is POINTER.
           03 WS-CALEN                 PIC S9(4) COMP.

      * Variables for time/date processing
       01  ABS-TIME                    PIC S9(8) COMP VALUE +0.
       01  TIME1                       PIC X(8)  VALUE SPACES.
       01  DATE1                       PIC X(10) VALUE SPACES.


      * Error Message Structure
       01  ERROR-MSG.
           03 EM-DATE                  PIC X(8)  VALUE SPACES.
           03 FILLER                   PIC X     VALUE SPACES.
           03 EM-TIME                  PIC X(6)  VALUE SPACES.
           03 FILLER                   PIC X(9)  VALUE ' LGAPBR01'.
           03 EM-VARIABLE.
             05 FILLER                 PIC X(6)  VALUE ' CNUM='.
             05 EM-CUSNUM              PIC X(10)  VALUE SPACES.
             05 FILLER                 PIC X(6)  VALUE ' PNUM='.
             05 EM-POLNUM              PIC X(10)  VALUE SPACES.
             05 FILLER                 PIC X(30) VALUE SPACES.

      * ODM Error Message
       01 WS-ERROR-MSGS.
          03 WS-HBR-STRING.
             05 WS-HBR-CALL  PIC X(8).
             05 FILLER       PIC X(4) VALUE "-WS>".
          03 WS-CC9          PIC 9(5).
          03 WS-RC9          PIC 9(5).
          03 WS-DISPLAY-MSG  PIC X(90) VALUE ALL SPACES.

       01  CA-ERROR-MSG.
           03 FILLER                   PIC X(9)  VALUE 'COMMAREA='.
           03 CA-DATA                  PIC X(90) VALUE SPACES.

      *----------------------------------------------------------------*
      * Definitions required for data manipulation                     *
      *----------------------------------------------------------------*
      * Fields to be used to check that commarea is correct length
       01  WS-COMMAREA-LENGTHS.
           03 WS-CA-HEADER-LEN         PIC S9(4) COMP VALUE +28.
           03 WS-REQUIRED-CA-LEN       PIC S9(4)      VALUE +0.

      *----------------------------------------------------------------*
      * ODM data structure definitions                                 *
      *----------------------------------------------------------------*
      * ODM supplied copybooks
       01 WS-REASON-CODES.
           COPY HBRC.
           COPY HBRWS.

      * Copybook to pass data to business rule execution server
           COPY LGCMARER.


      ******************************************************************
      *    L I N K A G E     S E C T I O N
      ******************************************************************
       LINKAGE SECTION.
      * Need to allow for max CICS commarea size of 32500 bytes
       01  DFHCOMMAREA.
             COPY LGCMAREA.


      ******************************************************************
      *    P R O C E D U R E S
      ******************************************************************
       PROCEDURE DIVISION.

      *----------------------------------------------------------------*
       MAINLINE SECTION.

      *----------------------------------------------------------------*
      * Common code                                                    *
      *----------------------------------------------------------------*
      * initialize working storage variables
           INITIALIZE WS-HEADER.
      * set up general variable
           MOVE EIBTRNID TO WS-TRANSID.
           MOVE EIBTRMID TO WS-TERMID.
           MOVE EIBTASKN TO WS-TASKNUM.
           MOVE EIBCALEN TO WS-CALEN.
      *----------------------------------------------------------------*

      *----------------------------------------------------------------*
      * Check commarea and obtain required details                     *
      *----------------------------------------------------------------*
      * If NO commarea received issue an ABEND
           IF EIBCALEN IS EQUAL TO ZERO
               MOVE ' NO COMMAREA RECEIVED' TO EM-VARIABLE
               PERFORM WRITE-ERROR-MESSAGE
               EXEC CICS ABEND ABCODE('LGCA') NODUMP END-EXEC
           END-IF

      * Convert incoming commarea to uppercase
      *  to ensure CHAR info is in upper case.
           MOVE FUNCTION UPPER-CASE(DFHCOMMAREA(1:EIBCALEN))
                TO DFHCOMMAREA(1:EIBCALEN)

      * initialize commarea return code to zero
           MOVE '00' TO CA-RETURN-CODE
           SET WS-ADDR-DFHCOMMAREA TO ADDRESS OF DFHCOMMAREA.

      *----------------------------------------------------------------*
      *    Define ruleset parameters and invoke rule execution server  *
      *----------------------------------------------------------------*
      * Initialise ruleset parameter data
           MOVE CA-REQUEST-ID TO REQUEST-ID
           MOVE CA-E-FUND-NAME TO FUND-NAME
           MOVE CA-E-TERM TO TERM
           MOVE CA-E-SUM-ASSURED TO SUM-ASSURED
           MOVE SPACES TO MSG

      * Move ruleset parameters to table HBRA-RA-PARMETERS

           MOVE ZERO               TO HBRA-CONN-RETURN-CODES
           MOVE LOW-VALUES         TO HBRA-RA-PARMETERS
           MOVE "/GenAppDemoApp/GenAppDemo"
                                   TO HBRA-CONN-RULEAPP-PATH
           MOVE LOW-VALUES         TO HBRA-RA-PARMETERS.

           MOVE 'request'         TO HBRA-RA-PARAMETER-NAME(1)
           MOVE LENGTH OF REQUEST TO HBRA-RA-DATA-LENGTH(1)
           SET HBRA-RA-DATA-ADDRESS(1)
                                   TO ADDRESS OF REQUEST

           MOVE 'response'             TO HBRA-RA-PARAMETER-NAME(2)
           MOVE LENGTH OF RESPONSE     TO HBRA-RA-DATA-LENGTH(2)
           SET HBRA-RA-DATA-ADDRESS(2)
                                   TO ADDRESS OF RESPONSE

      * Get connection to rule execution server
           CALL 'HBRCONN' USING HBRA-CONN-AREA.
           IF HBRA-CONN-COMPLETION-CODE NOT EQUAL HBR-CC-OK
              MOVE 'HBRCONN ' TO WS-HBR-CALL
              PERFORM ODM-ERROR-ROUTINE
           END-IF

      * Invoke rule execution server
           CALL 'HBRRULE' USING HBRA-CONN-AREA

           IF HBRA-CONN-COMPLETION-CODE NOT EQUAL HBR-CC-OK
              MOVE 'HBRRULE ' TO WS-HBR-CALL
              PERFORM ODM-ERROR-ROUTINE
           END-IF

      * Issue disconnect to rule execution server
           CALL 'HBRDISC' USING HBRA-CONN-AREA

           IF HBRA-CONN-COMPLETION-CODE NOT EQUAL HBR-CC-OK
              MOVE 'HBRDISC ' TO WS-HBR-CALL
              PERFORM ODM-ERROR-ROUTINE
           END-IF

      * Move returned fund name to commarea
           MOVE FUND-NAME TO CA-E-FUND-NAME

      * Return to caller
           EXEC CICS RETURN END-EXEC.

       MAINLINE-EXIT.
           EXIT.
      *----------------------------------------------------------------*

      *================================================================*
      * Procedure to write error message to TS QUEUE                   *
      *   message will include Date, Time, Program Name, Customer      *
      *   Number, and Policy Number.                                   *
      *================================================================*
       WRITE-ERROR-MESSAGE.
      * Obtain and format current time and date
           EXEC CICS ASKTIME ABSTIME(ABS-TIME)
           END-EXEC
           EXEC CICS FORMATTIME ABSTIME(ABS-TIME)
                     MMDDYYYY(DATE1)
                     TIME(TIME1)
           END-EXEC
           MOVE DATE1 TO EM-DATE
           MOVE TIME1 TO EM-TIME
      * Write output message to TSQ
           EXEC CICS LINK PROGRAM('LGSTSQ')
                     COMMAREA(ERROR-MSG)
                     LENGTH(LENGTH OF ERROR-MSG)
           END-EXEC.
      * Write 90 bytes or as much as we have of commarea to TDQ
           IF EIBCALEN > 0 THEN
             IF EIBCALEN < 91 THEN
               MOVE DFHCOMMAREA(1:EIBCALEN) TO CA-DATA
               EXEC CICS LINK PROGRAM('LGSTSQ')
                         COMMAREA(CA-ERROR-MSG)
                         LENGTH(LENGTH OF CA-ERROR-MSG)
               END-EXEC
             ELSE
               MOVE DFHCOMMAREA(1:90) TO CA-DATA
               EXEC CICS LINK PROGRAM('LGSTSQ')
                         COMMAREA(CA-ERROR-MSG)
                         LENGTH(LENGTH OF CA-ERROR-MSG)
               END-EXEC
             END-IF
           END-IF.
           EXIT.

      *================================================================*
      * Procedure to write error message to TS QUEUE                   *
      *   message will include Date, Time, Program Name, Customer      *
      *   Number, and Policy Number.                                   *
      *================================================================*
       ODM-ERROR-ROUTINE.
           MOVE HBRA-CONN-COMPLETION-CODE TO WS-CC9
           MOVE HBRA-CONN-REASON-CODE TO WS-RC9
           STRING WS-HBR-CALL
                 '-CC-' WS-CC9
                 '-RC-' WS-RC9
                 '-MSG-' HBRA-RESPONSE-MESSAGE(1:72)
                 DELIMITED BY '>'
                 INTO WS-DISPLAY-MSG
           EXEC CICS LINK PROGRAM('LGSTSQ')
                     COMMAREA(WS-DISPLAY-MSG)
                     LENGTH(LENGTH OF WS-DISPLAY-MSG)
           END-EXEC

           EXEC CICS ABEND ABCODE('LGBR') NODUMP END-EXEC

           EXEC CICS RETURN END-EXEC.

