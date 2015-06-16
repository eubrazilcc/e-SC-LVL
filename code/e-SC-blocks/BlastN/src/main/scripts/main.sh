#!/bin/bash

/usr/bin/printenv

ls

FORMAT=0

# Prepare the FORMAT variable depending on the property with options
case "$PROPS__Output_Format" in
"" | "pairwise") FORMAT=0 ;;
"query-anchored showing identities") FORMAT=1 ;;
"query-anchored no identities") FORMAT=2 ;;
"flat query-anchored show identities") FORMAT=3 ;;
"flat query-anchored no identinies") FORMAT=4 ;;
"XML Blast") FORMAT=5 ;;
"tabular") FORMAT=6 ;;
"tabular with comment lines") FORMAT=7 ;;
"text ASN.1") FORMAT=8 ;;
"binary ASN.1") FORMAT=9 ;;
"CSV") FORMAT=10 ;;
"BLAST archive format") FORMAT=11 ;;
"JSON Seqalign") FORMAT=12 ;;
*)
    echo "Unsupported format option: $PROPS__Output_Format"
    exit 1
esac

# Make sure the output filename do not overwrite any other file.
OUTFILE=$( mktemp output-XXXX.blastn )

if [ -z "$INPUTS__blast_database__db_file" ]; then
    # Prepare blast database from the input database fasta file
    ${DEPS__BlastPlus_2_2_30__makeblastdb} -dbtype nucl -in ${INPUTS__database_fasta}
    ${DEPS__BlastPlus_2_2_30__blastn} -db ${INPUTS__database_fasta} -query ${INPUTS__input_fasta} -outfmt $FORMAT > $OUTFILE
else
    # Use the blast database provided via the library wrapper input
    ${DEPS__BlastPlus_2_2_30__blastn} -db ${INPUTS__blast-database__db_file} -query ${INPUTS__input_fasta} -outfmt $FORMAT > $OUTFILE
fi

# Return the output file through block output port 'output_file'
echo "Trying to write ${OUTFILE} to the ${OUTPUTS__output_file} file."
echo ""
echo "$OUTFILE" >> "${OUTPUTS__output_file}"
