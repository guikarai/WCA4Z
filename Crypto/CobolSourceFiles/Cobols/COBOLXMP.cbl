      **************************
       IDENTIFICATION DIVISION.
      ***************************
       PROGRAM-ID. COBOLXMP.
      *****************************
       ENVIRONMENT DIVISION.
      *****************************************************************
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-370.
       OBJECT-COMPUTER. IBM-370.
      *****************************
       DATA DIVISION.
      *****************************************************************
       FILE SECTION.
       WORKING-STORAGE SECTION.
       77 INPUT-TEXT              PIC X(24)
                                            VALUE
                                             'ABCDEFGHIJKLMN0987654321'.
       77 OUTPUT-TEXT             PIC X(24)
                                            VALUE LOW-VALUES.
       77 COMPARE-TEXT            PIC X(24)
                                            VALUE LOW-VALUES.
       77 CIPHER-PROCESSING-RULE  PIC X(08)
                                            VALUE 'CUSP '.
       77 KEY-FORM                PIC X(08)
                                            VALUE 'OP '.
       77 KEY-LENGTH              PIC X(08)
                                            VALUE 'SINGLE '.
       77 KEY-TYPE-1              PIC X(08)
                                            VALUE 'DATA '.
       77 KEY-TYPE-2              PIC X(08)
                                            VALUE ' '.
       77 ICV                     PIC X(08)
                                            VALUE LOW-VALUES.
       77 PAD                     PIC X(01)
                                            VALUE LOW-VALUES.
      ************* DEFINE SAPI INPUT/OUTPUT PARAMETERS ************
       01 SAPI-REC.
          05 RETURN-CODE-S        PIC 9(08) COMP.
          05 REASON-CODE-S        PIC 9(08) COMP.
          05 EXIT-DATA-LENGTH-S   PIC 9(08) COMP.
          05 EXIT-DATA-S          PIC X(04).
          05 KEK-KEY-ID-1-S       PIC X(64)
                                            VALUE LOW-VALUES.
          05 KEK-KEY-ID-2-S       PIC X(64)
                                            VALUE LOW-VALUES.
          05 DATA-KEY-ID-S        PIC X(64)
                                            VALUE LOW-VALUES.
          05 NULL-KEY-ID-S        PIC X(64)
                                            VALUE LOW-VALUES.
          05 KEY-FORM-S           PIC X(08).
          05 KEY-LENGTH-S         PIC X(08).
          05 DATA-KEY-TYPE-S      PIC X(08).
          05 NULL-KEY-TYPE-S      PIC X(08).
          05 TEXT-LENGTH-S        PIC 9(08) COMP.
          05 TEXT-S               PIC X(24).
          05 ICV-S                PIC X(08).
          05 PAD-S                PIC X(01).
          05 CPHR-TEXT-S          PIC X(24).
          05 COMP-TEXT-S          PIC X(24).
          05 RULE-ARRAY-COUNT-S   PIC 9(08) COMP.
          05 RULE-ARRAY-S.
             10 RULE-ARRAY        PIC X(08).
          05 CHAINING-VECTOR-S    PIC X(18).
      *****************************************************************
       PROCEDURE DIVISION.
      *****************************************************************
       MAIN-RTN.
      ************* CALL KEY GENERATE ***************************
           MOVE 0 TO EXIT-DATA-LENGTH-S.
           MOVE KEY-FORM TO KEY-FORM-S.
           MOVE KEY-LENGTH TO KEY-LENGTH-S.
           MOVE KEY-TYPE-1 TO DATA-KEY-TYPE-S.
           MOVE KEY-TYPE-2 TO NULL-KEY-TYPE-S.
           CALL 'CSNBKGN' USING RETURN-CODE-S
              REASON-CODE-S
              EXIT-DATA-LENGTH-S
              EXIT-DATA-S
              KEY-FORM-S
              KEY-LENGTH-S
              DATA-KEY-TYPE-S
              NULL-KEY-TYPE-S
              KEK-KEY-ID-1-S
              KEK-KEY-ID-2-S
              DATA-KEY-ID-S
              NULL-KEY-ID-S.
           IF RETURN-CODE-S NOT = 0 OR
              REASON-CODE-S NOT = 0 THEN
              DISPLAY '*** KEY-GENERATE ***'
              DISPLAY '*** RETURN-CODE = ' RETURN-CODE-S
              DISPLAY '*** REASON-CODE = ' REASON-CODE-S
           ELSE
              MOVE 24 TO TEXT-LENGTH-S
              MOVE INPUT-TEXT TO TEXT-S
              MOVE 1 TO RULE-ARRAY-COUNT-S
              MOVE CIPHER-PROCESSING-RULE TO RULE-ARRAY-S
              MOVE LOW-VALUES TO CHAINING-VECTOR-S
              MOVE ICV TO ICV-S.
           MOVE PAD TO PAD-S.
      ************* CALL ENCIPHER ************************************
           CALL 'CSNBENC' USING RETURN-CODE-S
              REASON-CODE-S
              EXIT-DATA-LENGTH-S
              EXIT-DATA-S
              DATA-KEY-ID-S
              TEXT-LENGTH-S
              TEXT-S
              ICV-S
              RULE-ARRAY-COUNT-S
              RULE-ARRAY-S
              PAD-S
              CHAINING-VECTOR-S
              CPHR-TEXT-S
           IF RETURN-CODE-S NOT = 0 OR
              REASON-CODE-S NOT = 0 THEN
              DISPLAY '*** ENCIPHER ***'
              DISPLAY '*** RETURN-CODE = ' RETURN-CODE-S
              DISPLAY '*** REASON-CODE = ' REASON-CODE-S
           ELSE
      ************* CALL DECIPHER ************************************

              CALL RULE-ARRAY-S
              CALL RULE-ARRAY-COUNT-S


              CALL 'CSNBDEC' USING RETURN-CODE-S
                 REASON-CODE-S
                 EXIT-DATA-LENGTH-S
                 EXIT-DATA-S
                 DATA-KEY-ID-S
                 TEXT-LENGTH-S
                 CPHR-TEXT-S
                 ICV-S
                 RULE-ARRAY-COUNT-S
                 RULE-ARRAY-S
                 CHAINING-VECTOR-S
                 COMP-TEXT-S
              IF RETURN-CODE-S NOT = 0 OR
                 REASON-CODE-S NOT = 0 THEN
                 DISPLAY '*** DECIPHER ***'
                 DISPLAY '*** RETURN-CODE = ' RETURN-CODE-S
                 DISPLAY '*** REASON-CODE = ' REASON-CODE-S
              ELSE
                 IF COMP-TEXT-S = TEXT-S THEN
                    DISPLAY '*** DECIPHERED TEXT = PLAIN TEXT ***'
                 ELSE
             DISPLAY '*** DECIPHERED TEXT ï¿½= PLAIN TEXT ***'.
             DISPLAY '*** TEST PROGRAM ENDED ***'
           STOP RUN.
