<?php

require_once("../../common.php");
if( isLoggedIn() && isset( $_REQUEST['DETAIL'] ) ){

	$mydb = $META->get_results( "
		SELECT C_BASECODE, C_NAME, C_HLEVEL, N, COUNT(DISTINCT C_FULLNAME) AS CHILDREN, COUNT(DISTINCT FULLER_NAME) AS ALL_CHILDREN, PATH FROM
		(
		    SELECT MAIN.*, FLOWSHEETS.C_FULLNAME, ALLCHILDREN.C_FULLNAME AS FULLER_NAME
				FROM
				(
					SELECT C_BASECODE, C_NAME, MAX(C_FULLNAME) AS PATH, COUNT(*) AS N, C_HLEVEL, MAX(C_TOTALNUM)
					FROM (
						SELECT *
						FROM FLOWSHEETS
		                WHERE
		                ".(is_numeric($_REQUEST['DETAIL']) ?
		                	(" C_BASECODE = 'FLO:". f($_REQUEST['DETAIL'])."'" ) :
		                	(" C_NAME LIKE '%". strtoupper(f($_REQUEST['DETAIL']))."%'")
		                   )." AND ROWNUM < 500
					) A
					GROUP BY C_BASECODE, C_NAME, C_HLEVEL
				) MAIN
				LEFT JOIN FLOWSHEETS ON (
		      INSTR(FLOWSHEETS.C_FULLNAME,MAIN.PATH) = 1 AND
		      FLOWSHEETS.C_HLEVEL = MAIN.C_HLEVEL + 1 AND
		      NOT FLOWSHEETS.C_VISUALATTRIBUTES LIKE 'F%'
		    )
		    LEFT JOIN FLOWSHEETS ALLCHILDREN ON (
		      INSTR(ALLCHILDREN.C_FULLNAME,MAIN.PATH) = 1 AND
		      ALLCHILDREN.C_HLEVEL > MAIN.C_HLEVEL
		    )
		) LASTER
		GROUP BY C_BASECODE, C_NAME, PATH, N, C_HLEVEL
		ORDER BY COUNT(*) DESC
		",OBJECT
	);
	if( $mydb ){
		foreach( $mydb as &$item ){
			include( 'i2b2_FLOW_ITEM_DISPLAY.php' );
		}
	}

}

?>
