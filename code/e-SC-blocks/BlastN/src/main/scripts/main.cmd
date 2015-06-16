@ECHO OFF
SETLOCAL

SET

SET FORMAT=0

:: Prepare the FORMAT variable depending on the property with options
IF "%PROPS__Output_Format%" == "pairwise" SET FORMAT=0
ELSE IF "%PROPS__Output_Format%" == "query-anchored showing identities" SET FORMAT=1
ELSE IF "%PROPS__Output_Format%" == "query-anchored no identities" SET FORMAT=2
ELSE IF "%PROPS__Output_Format%" == "flat query-anchored show identities" SET FORMAT=3
ELSE IF "%PROPS__Output_Format%" == "flat query-anchored no identinies" SET FORMAT=4
ELSE IF "%PROPS__Output_Format%" == "XML Blast" SET FORMAT=5
ELSE IF "%PROPS__Output_Format%" == "tabular" SET FORMAT=6
ELSE IF "%PROPS__Output_Format%" == "tabular with comment lines" SET FORMAT=7
ELSE IF "%PROPS__Output_Format%" == "text ASN.1" SET FORMAT=8
ELSE IF "%PROPS__Output_Format%" == "binary ASN.1" SET FORMAT=9
ELSE IF "%PROPS__Output_Format%" == "CSV" SET FORMAT=10
ELSE IF "%PROPS__Output_Format%" == "BLAST archive format" SET FORMAT=11
ELSE IF "%PROPS__Output_Format%" == "JSON Seqalign" SET FORMAT=12
ELSE (
    ECHO "Unsupported format option: %PROPS__Output_Format%"
    EXIT 1
)

:: Prepare temporary file name
:MKTEMP
SET /a OUTFILE=%RANDOM%+100000
SET OUTFILE=output-%OUTFILE:~5%
IF EXIST %OUTFILE% GOTO MKTEMP


IF DEFINED INPUTS__blast_database__db_file (
    :: Prepare blast database from the input database fasta file
    %DEPS__BlastPlus_2_2_30__makeblastdb% -dbtype nucl -in %INPUTS__database_fasta%
    %DEPS__BlastPlus_2_2_30__blastn% -db %INPUTS__database_fasta% -query %INPUTS__input_fasta% -outfmt %FORMAT% > %OUTFILE%
) ELSE (
    :: Use the blast database provided via the library wrapper input
    %DEPS__BlastPlus_2_2_30__blastn% -db %INPUTS__blast-database__db_file% -query %INPUTS__input_fasta% -outfmt %FORMAT% > %OUTFILE%
)

ECHO %OUTFILE% >> %OUTPUTS__output_file%
