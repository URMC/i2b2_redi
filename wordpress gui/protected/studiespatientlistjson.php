<?php

	/**
	 * Copyright 2015 , University of Rochester Medical Center
	 *
	 * Original Source Licence:
	 * MIT license
     *
     * Permission is hereby granted, free of charge, to any person obtaining a
     * copy of this software and associated documentation files (the "Software"),
     * to deal in the Software without restriction, including without limitation
     * the rights to use, copy, modify, merge, publish, distribute, sublicense,
     * and/or sell copies of the Software, and to permit persons to whom the
     * Software is furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included
     * in all copies or substantial portions of the Software.
	 *
	 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
	 * THE SOFTWARE.
	 *
	 * @author png (phillip_ng@urmc.rochester.edu)
	 */

	/**
	 * This page provides the datasource to populate the datatable in studiespatientmgmt.php,
	 * and it was a rewrite and hardening of the datatable JSON source:
	 * https://www.datatables.net/examples/data_sources/ajax.html
	 *
	 *
	 * This page is downstream from studiespatientmgmt.php
 	 */

	require_once("../common.php");

	/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
	 * Easy set variables
	 */

	/* Array of database columns which should be read and sent back to DataTables. Use a space where
	 * you want to insert a non-database field (for example a counter or static image)
	 */

	$aColumns = array( 'TO_CHAR(SYSID)', 'STUDYID', 'MRN', 'SITE', 'FIRSTNAME', 'LASTNAME', "TO_CHAR(DOB_DATE,'MM/DD/YYYY HH24:MI')", "TO_CHAR(ENROLLED,'MM/DD/YYYY HH24:MI')",'ARM', 'MESSAGES'  );

	/* Indexed column (used for fast and accurate table cardinality) */
	$sIndexColumn = "SYSID";

	/* DB table to use */
	$sTable = "ENROLLED_PATIENT";

	/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
	 * If you just want to use the basic configuration for DataTables with PHP server-side, there is
	 * no need to edit below this line
	 */

	/*
	 * Local functions
	 */
	function fatal_error ( $sErrorMessage = '' ){
		header( $_SERVER['SERVER_PROTOCOL'] .' 500 Internal Server Error' );
		die( $sErrorMessage );
	}


	/*
	 * Ordering
	 */
	$sOrder = "";
	if ( isset( $_GET['iSortCol_0'] ) )
	{
		$sOrder = "ORDER BY  ";
		for ( $i=0 ; $i<intval( $_GET['iSortingCols'] ) && $i < 255 ; $i++ )
		{
			if ( $_GET[ 'bSortable_'.intval($_GET['iSortCol_'.$i]) ] == "true" )
			{
				$sOrder .= "".$aColumns[ intval( $_GET['iSortCol_'.$i] ) ]." ".
					($_GET['sSortDir_'.$i]==='asc' ? 'asc' : 'desc') .", ";
			}
		}

		$sOrder = substr_replace( $sOrder, "", -2 );
		if ( $sOrder == "ORDER BY" )
		{
			$sOrder = "";
		}
	}


	/*
	 * Filtering
	 * NOTE this does not match the built-in DataTables filtering which does it
	 * word by word on any field. It's possible to do here, but concerned about efficiency
	 * on very large tables, and MySQL's regex functionality is very limited
	 */
	$sWhere = "";
	if ( isset($_GET['sSearch']) && $_GET['sSearch'] != "" )
	{
		$sWhere = "WHERE (";
		for ( $i=0 ; $i<count($aColumns) ; $i++ )
		{
			if ( isset($_GET['bSearchable_'.$i]) && $_GET['bSearchable_'.$i] == "true" )
			{
				$sWhere .= "".$aColumns[$i]." LIKE '%".filefix( $_GET['sSearch'] )."%' OR ";
			}
		}
		$sWhere = substr_replace( $sWhere, "", -3 );
		$sWhere .= ')';
	}

	if( isset($_GET['PROJECTID'])){
		if( $sWhere == "" ){
			$sWhere = "WHERE 1=1 ";
		}
		//add item for determinine project code.
		$sWhere .= "AND PROJECTID = ".intval($_GET['PROJECTID'])."";
	}

	/* Individual column filtering */
	for ( $i=0 ; $i<count($aColumns) ; $i++ )
	{
		if ( isset($_GET['bSearchable_'.$i]) && $_GET['bSearchable_'.$i] == "true" && $_GET['sSearch_'.$i] != '' )
		{
			if ( $sWhere == "" )
			{
				$sWhere = "WHERE ";
			}
			else
			{
				$sWhere .= " AND ";
			}
			$sWhere .= "`".$aColumns[$i]."` LIKE '%".filefix($_GET['sSearch_'.$i])."%' ";
		}
	}


	/*
	 * SQL queries
	 * Get data to display
	 */

	/*
	 * Paging
	 */
	$sLimit = "";
	if ( isset( $_GET['iDisplayStart'] ) && $_GET['iDisplayLength'] != '-1' )
	{
		$sLimit = "WHERE RNUM BETWEEN ".intval( $_GET['iDisplayStart'] )." AND ".(intval( $_GET['iDisplayStart'] )+intval( $_GET['iDisplayLength'] )-(intval( $_GET['iDisplayStart'] ) == 0 ? 0 : 1));
	}


	$sQuery = " SELECT * FROM ( SELECT SORTED.*, ROWNUM AS RNUM FROM ( SELECT $sIndexColumn , ".str_replace(" , ", " ", implode(", ", $aColumns))." FROM $sTable $sWhere $sOrder ) SORTED ) $sLimit ";
	$mydb = $DB->get_results( $sQuery, ARRAY_N );

	/* Data set length after filtering */
	$pest = $DB->get_row("SELECT COUNT(*) AS COUNTER FROM ( SELECT $sIndexColumn FROM $sTable $sWhere )");
	$iFilteredTotal = $pest->COUNTER;

	/* Total data set length */
	$pest = $DB->get_row("SELECT COUNT(".$sIndexColumn.") AS COUNTER FROM $sTable WHERE PROJECTID = ".intval($_GET['PROJECTID'])."");
	$iTotal = $pest->COUNTER;


	/*
	 * Output
	 */
	$output = array(
		"sEcho" => intval($_GET['sEcho']),
		"iTotalRecords" => $iTotal,
		"iTotalDisplayRecords" => $iFilteredTotal,
		"aaData" => array(),
		"eDbErrors" => $EZSQL_ERROR
	);

	foreach($mydb as &$aRow){
		$row = array();
		for ( $i=0 ; $i<count($aColumns) ; $i++ ){

			$row["DT_RowId"] = $aRow[0]; //$sIndexColumn

			if ( $aColumns[$i] != ' ' ){
				/* General output */
				$data = $aRow[ $i+1 ];
				if( strlen($data) == 16 && substr( $data, -5 ) == '00:00' ){
					$data = substr( $data, 0,10 );
				}
				$row[] = $data;

			}
		}
		$output['aaData'][] = $row;
	}

	echo json_encode( $output );
?>