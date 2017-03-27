/**
 * Copyright 2015 , University of Rochester Medical Center
 * 
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
package edu.rochester.urmc.util;
/**
 * @version	$Id: SQLUtilities.java,v 1.1 2005/07/27 14:12:32 png Exp png $
 *
 * @author	Philip Ng
 *
 * Revisions:
 *              Revision 1.9    2016/03/10    cculbertson1
 *              Added getMaxValue method
 * 
 *		$Log: SQLUtilities.java,v $
 *		Revision 1.1  2005/07/27 14:12:32  png
 *		Initial revision
 *
 *		Revision 1.1  2005/06/07 20:29:24  png
 *		Initial revision
 *
 *		Revision 1.3  2005/04/01 13:49:16  png
 *		*** empty log message ***
 *
 *		Revision 1.2  2005/01/04 19:35:06  png
 *		before figuring where memory leak is
 *
 *		Revision 1.1  2004/10/28 14:44:01  png
 *		Initial revision
 *
 *		Revision 1.5  2004/09/29 16:49:43  png
 *		works...
 *
 *		Revision 1.4  2004/09/27 16:28:39  png
 *		*** empty log message ***
 *
 *		Revision 1.3  2004/09/02 17:57:25  png
 *		*** empty log message ***
 *
 *		Revision 1.2  2004/08/26 21:33:08  png
 *		*** empty log message ***
 *
 *		Revision 1.1  2004/08/26 16:14:25  png
 *		Initial revision
 *
 *		Revision 1.8  2004/07/20 12:34:30  png
 *		before major multiplexing culticasting attempt
 *
 *		Revision 1.7  2004/07/06 19:07:15  png
 *		before Verifier inclusion
 *
 *		Revision 1.6  2004/06/18 18:21:34  png
 *		after dumb JComboBox issue. Optimized removed some DEG's
 *		listener code
 *
 *		Revision 1.5  2004/06/16 20:05:24  png
 *		after many improvements and adding a converter system.
 *
 *		Revision 1.4  2004/06/09 20:20:46  png
 *		debugged most of DataEntryGui's minor bugs
 *
 *		Revision 1.3  2004/06/08 18:37:50  png
 *		After buffering of DataEntryGui
 *
 *		Revision 1.2  2004/06/07 17:07:41  png
 *		before DataEntryGui rewrite
 *
 *		Revision 1.1  2004/06/02 18:52:01  png
 *		Initial revision
 *
 */


import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class was designed to retrieve small snippets of queries, returning
 * at most MAX_RECORDS_RETRIEVED (default 25) in a linked list of arrays of
 * objects.
 */
public class SQLUtilities implements Iterator, Iterable {

    public static boolean DEBUG_MODE = false;

    public String sqlQuery = "";
    int maxReturned = 0;

    static int OPEN_CURSORS = 0;
    
    HashMap lastresult = new HashMap();

    public String[] fieldnames = null;

    public static void execSQL( Connection aDB, String query ) throws SQLException {
        Statement statmnt = null;
        long start = new Date().getTime();
        try{
            if( aDB != null ){
                if( DEBUG_MODE ){ System.out.print("OPEN:EXECSQL:" + ++OPEN_CURSORS); }
                if( DEBUG_MODE ){ System.out.print("\tSQLUTILS: EXECSQL:" + query ); }
                statmnt = aDB.createStatement();
                statmnt.executeUpdate(query);
                try{
                    long end = new Date().getTime();
                    statmnt.close();
                    if( DEBUG_MODE ){ System.out.println("\tCLOSED:EXEC:" + (--OPEN_CURSORS) + (end-start)+" ms"); }
                }catch (Exception ex){}
            }
        } catch (Exception ex){
            try{
                long end = new Date().getTime();
                statmnt.close();
                if( DEBUG_MODE ){ System.out.println("\tCLOSED:EXEC:" + (--OPEN_CURSORS) + (end-start)+" ms"); }
            }catch (Exception ex1){}
            throw new SQLException( ex.getMessage() + ":" + query );
        }
    }

    public static boolean existsTable( Connection aDB, String query ) {
        boolean answer = false;
        try{
            SQLUtilities.getTable( aDB, "SELECT * FROM " + query ,1 );
            answer= true;
        }catch (Exception ex){}
        return answer;
    }
    private Statement mySqlStatement = null;
    private ResultSet myData = null;
    private int[] myTypes = null;
    public String[] myNames = null;
    private int size = -1;
    public String myTableName = "";

    /*public static void main( String args[] )throws Exception{
        String driver = "sun.jdbc.odbc.JdbcOdbcDriver";
        String db_url = "jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ=C:\\Documents and Settings\\png\\My Documents\\ProtocolHVTN\\HVTN_97\\HVTN2003_97.mdb";
        Class.forName( driver );
        Connection myConnection = java.sql.DriverManager.getConnection( db_url, "" ,"" );
        new SQLUtilities( myConnection, "SELECT * FROM SCREENINGS");
    }*/
    public SQLUtilities( Connection aDB, String sqlQuer ) throws Exception {
        this( aDB, sqlQuer, true );
    }
    public SQLUtilities( Connection aDB, String sqlQuer, boolean meta ) throws Exception {
        if( aDB != null ){
            if( DEBUG_MODE ){ System.out.print("SQLUTILS: <INIT>:" + sqlQuer ); }
            //make a connection, look a vanilla version!
            Connection myConnection = aDB;
            sqlQuery = sqlQuer; //"SELECT COUNT(*) FROM ("+sqlQuer+") A
            if( meta ){
                //.substring(sqlQuer.toUpperCase().indexOf(" FROM ")+6))[0][0].toString()
                try{ 
                    String sizer = sqlQuer.toUpperCase();
                    if(sizer.indexOf(" ORDER BY ") > 0 ){
                        sizer = sqlQuer.substring(0,sizer.indexOf(" ORDER BY "));
                    }
                    size = Integer.parseInt(SQLUtilities.getTableArray( aDB, "SELECT COUNT(*) FROM ("+sizer+") ZZZ")[0][0].toString());
                }catch(Exception ex){}
                if( DEBUG_MODE ){ System.out.println( "SQLUTILS: <INIT>:SIZED: " + size ); }
            }
            myTableName = sqlQuer.substring(sqlQuery.indexOf(" FROM ")+6);
            mySqlStatement = myConnection.createStatement();
            myData = mySqlStatement.executeQuery( sqlQuery );
            ResultSetMetaData rsmd = myData.getMetaData();
            myTypes = new int[ rsmd.getColumnCount() + 1 ];
            myNames = new String[ rsmd.getColumnCount() ];
            for( int i = 1; i < myTypes.length; i++ ){
                myTypes[i] = rsmd.getColumnType(i);
                myNames[i-1] = rsmd.getColumnName(i);

            }
            rsmd = null;
            myNames=myNames;
        }
    }

    public int getSize(){ return size; }
    public void close(){
        try{
            myData.close();
        }catch(Exception ex){}
        try{
            mySqlStatement.close();
        }catch(Exception ex){}

    }

    public Object[] getNextLine() throws Exception {
        Object[] ans = new Object[ myTypes.length -1];
        myData.next();
        for( int i = 1; i < myTypes.length ; i++ ){
            ans[ i-1 ] = getObject( myData, i, myTypes[i] );
        }

        return ans;
    }

    public HashMap getNextHashLine() throws Exception {
        HashMap ans = null;
        if( DEBUG_MODE ){ System.out.print("GETNEXTLINE:"); }
        try{
            myData.next();
            ans = new HashMap();
            for( int i = 1; i < myTypes.length ; i++ ){
                Object datum = getObject( myData, i, myTypes[i] );
                Object key = (myNames[i-1] == null ? "" :myNames[i-1]).toUpperCase();
                ans.put(key,datum );
            }
        } catch( Exception ex ){
            if(DEBUG_MODE){ ex.printStackTrace(); }
            ans = null;
        }
        if( DEBUG_MODE ){ System.out.println("done w/ " + (ans == null ? "" : ans.size() + " columns : \t " + ans ) ); }
        return ans;
    }
    public String[] getFieldNames(){
        return myNames;
    }
    public static String[] getFieldNames(Connection aDB, String sqlQuery ) throws Exception {

        String[] ans = null;
        if( aDB != null ){

            //make a connection, look a vanilla version!
            Connection myConnection = aDB;
            Statement mySqlStatement = myConnection.createStatement();
            ResultSet myResultSet = mySqlStatement.executeQuery( sqlQuery );

            //retrieve the table data, specifically the integer SQL types so we
            //can cast them appropriately.
            ResultSetMetaData rsmd = myResultSet.getMetaData();

            //get the column count, make an array.
            ans = new String[ rsmd.getColumnCount() ];
            for( int i = 1; i < ans.length + 1 ; i++ ){
                ans[ i-1 ] = rsmd.getColumnName( i );
                //System.out.println( Integer.toString(i) + ans[i-1] );
            }
            try{myResultSet.close();} catch (Exception e){}
            try{mySqlStatement.close();} catch (Exception e){}
            myResultSet = null; mySqlStatement = null;
        }

        return ans;
    }
    /**
     * This method dumps the table out to a linked list array of objects.
     *
     * @param aDB - a connection to the database.
     * @param sqlQuery - the SQL Query which you want a output to.
     * @param maxReturned - retrieve this many records, <=0  means all
     */
    public static LinkedList getTable( Connection aDB, String sqlQuery)
        throws Exception{ return getTable( aDB, sqlQuery, -1 );}

    public static LinkedList getTable( Connection aDB, String sqlQuery,
        int maxReturned ) throws Exception{




        LinkedList answer = new LinkedList();

        Statement mySqlStatement = null;
        ResultSet myResultSet    = null;

        try{
            if( aDB != null ){
                if( DEBUG_MODE ){ System.out.print("OPEN:GETTABLE:" + ++OPEN_CURSORS); }
                if( DEBUG_MODE ){ System.out.print("\tSQLUTILS: GETTABLE:" + sqlQuery ); }
                //make a connection, look a vanilla version
                mySqlStatement = aDB.createStatement();
                myResultSet = mySqlStatement.executeQuery( sqlQuery );


                //retrieve the table data, specifically the integer SQL types so we
                //can cast them appropriately.
                ResultSetMetaData rsmd = myResultSet.getMetaData();

                //get the column count, make an array.
                int types[] = new int[ rsmd.getColumnCount() + 1 ];

                for( int i = 1; i < types.length; i++ ){
                    types[i] = rsmd.getColumnType(i);
                }



                //make the arrays and tar them to the end, going forward only.
                int counter = maxReturned;
                while ( ( ( counter > 0 && maxReturned > 0 ) ||
                         maxReturned <= 0 ) && myResultSet.next() ){


                    counter--;
                    Object[] ans = new Object[ types.length - 1 ];

                    for( int i = 1; i < types.length ; i++ ){

                        ans[ i-1 ] = getObject( myResultSet, i, types[i] );
                        //System.out.println( Integer.toString(i) + ans[i-1] );

                    }
                    answer.add( ans );

                }
            }

            if( myResultSet != null ){ try{myResultSet.close();} catch (Exception e){} }
            if( myResultSet != null ){ try{mySqlStatement.close();} catch (Exception e){} }
            if( DEBUG_MODE ){ System.out.println("\tCLOSED:GETTABLE:" + --OPEN_CURSORS); }
            myResultSet = null; mySqlStatement = null;

        } catch ( Exception ex ){

            if( myResultSet != null ){ try{myResultSet.close();} catch (Exception e){} }
            if( myResultSet != null ){ try{mySqlStatement.close();} catch (Exception e){} }
            if( DEBUG_MODE ){ System.out.println("\tCLOSED:GETTABLE:" + --OPEN_CURSORS); }
            myResultSet = null; mySqlStatement = null;

            throw ex;
        }
        return answer;

    }
    
    /**
     * This method dumps the table out to an array list array of objects.
     *
     * @param aDB - a connection to the database.
     * @param sqlQuery - the SQL Query which you want a output to.
     * @param maxReturned - retrieve this many records, <=0  means all
     */
    public static ArrayList getALTable( Connection aDB, String sqlQuery)
        throws Exception{ return getALTable( aDB, sqlQuery, -1 );}

    public static ArrayList getALTable( Connection aDB, String sqlQuery,
        int maxReturned ) throws Exception{




        ArrayList answer = new ArrayList();

        Statement mySqlStatement = null;
        ResultSet myResultSet    = null;

        try{
            if( aDB != null ){
                if( DEBUG_MODE ){ System.out.print("OPEN:GETTABLE:" + ++OPEN_CURSORS); }
                if( DEBUG_MODE ){ System.out.print("\tSQLUTILS: GETTABLE:" + sqlQuery ); }
                //make a connection, look a vanilla version
                mySqlStatement = aDB.createStatement();
                myResultSet = mySqlStatement.executeQuery( sqlQuery );


                //retrieve the table data, specifically the integer SQL types so we
                //can cast them appropriately.
                ResultSetMetaData rsmd = myResultSet.getMetaData();

                //get the column count, make an array.
                int types[] = new int[ rsmd.getColumnCount() + 1 ];

                for( int i = 1; i < types.length; i++ ){
                    types[i] = rsmd.getColumnType(i);
                }



                //make the arrays and tar them to the end, going forward only.
                int counter = maxReturned;
                while ( ( ( counter > 0 && maxReturned > 0 ) ||
                         maxReturned <= 0 ) && myResultSet.next() ){


                    counter--;
                    Object[] ans = new Object[ types.length - 1 ];

                    for( int i = 1; i < types.length ; i++ ){

                        ans[ i-1 ] = getObject( myResultSet, i, types[i] );
                        //System.out.println( Integer.toString(i) + ans[i-1] );

                    }
                    answer.add( ans );

                }
            }

            if( myResultSet != null ){ try{myResultSet.close();} catch (Exception e){} }
            if( myResultSet != null ){ try{mySqlStatement.close();} catch (Exception e){} }
            if( DEBUG_MODE ){ System.out.println("\tCLOSED:GETTABLE:" + --OPEN_CURSORS); }
            myResultSet = null; mySqlStatement = null;

        } catch ( Exception ex ){

            if( myResultSet != null ){ try{myResultSet.close();} catch (Exception e){} }
            if( myResultSet != null ){ try{mySqlStatement.close();} catch (Exception e){} }
            if( DEBUG_MODE ){ System.out.println("\tCLOSED:GETTABLE:" + --OPEN_CURSORS); }
            myResultSet = null; mySqlStatement = null;

            throw ex;
        }
        return answer;

    }
    /**
     * Returns the maximum value for the specified table column
     * 
     * @author cculbertson1
     * 
     * @param aDB the database connection to use
     * @param schema the schema to use
     * @param table the table to use
     * @param column the column to use
     * @return MAX(column)
     * @throws Exception 
     */
    public static BigDecimal getMaxValue(Connection aDB, String schema, String table, String column) throws Exception {
        try(Statement mySqlStatement = aDB.createStatement()) {
            try(ResultSet myResultSet = mySqlStatement.executeQuery(
                    "SELECT MAX(" + column + ") AS " + column + " " +
                    "FROM " + schema + "." + table)) {
                if(myResultSet.next()) {
                    return myResultSet.getBigDecimal(column);
                } else {
                    return null;
                }                
            }
        }
    }

    public static LinkedList<HashMap> getHashedTable( Connection aDB, String sqlQuery)
        throws Exception{ return getHashedTable( aDB, sqlQuery, -1 );}

    public static LinkedList<HashMap> getHashedTable( Connection aDB, String sqlQuery, int maxReturned ) throws Exception{

        Statement mySqlStatement = null;
        ResultSet myResultSet = null;

        LinkedList<HashMap> answer = new LinkedList<HashMap>();
        try{
            if( aDB != null ){
                if( DEBUG_MODE ){ System.out.print("OPEN:GETHASHED:" + ++OPEN_CURSORS); }
                if( DEBUG_MODE ){ System.out.print("\tSQLUTILS: GETHASHTABLE:" + sqlQuery ); }
                //make a connection, look a vanilla version!
                mySqlStatement = aDB.createStatement();
                myResultSet = mySqlStatement.executeQuery( sqlQuery );

                //retrieve the table data, specifically the integer SQL types so we
                //can cast them appropriately.
                ResultSetMetaData rsmd = myResultSet.getMetaData();

                //get the column count, make an array.
                int types[] = new int[ rsmd.getColumnCount() + 1 ];
                String names[] = new String[ rsmd.getColumnCount() + 1 ];

                for( int i = 1; i < types.length; i++ ){
                    types[i] = rsmd.getColumnType(i);
                    names[i] = rsmd.getColumnName(i);
                }

                //make the arrays and tar them to the end, going forward only.
                int counter = maxReturned;
                while ( ( ( counter > 0 && maxReturned > 0 ) || maxReturned <= 0 ) && myResultSet.next() ){

                    HashMap entry = new HashMap();

                    counter--;

                    for( int i = 1; i < types.length ; i++ ){

                        entry.put( names[ i ].trim().toUpperCase(), getObject( myResultSet, i, types[i] ) );

                    }
                    answer.add( entry );

                }
                if(myResultSet != null ){ try{myResultSet.close();} catch (Exception e){} }
                if(mySqlStatement != null ){ try{mySqlStatement.close();} catch (Exception e){} }
                myResultSet = null; mySqlStatement = null;
                if( DEBUG_MODE ){ System.out.println("\tCLOSED:GETHASHED:" + --OPEN_CURSORS); }
            }
        } catch (Exception ex){
            if(myResultSet != null ){ try{myResultSet.close();} catch (Exception e){} }
            if(mySqlStatement != null ){ try{mySqlStatement.close();} catch (Exception e){} }
            myResultSet = null; mySqlStatement = null;
            if( DEBUG_MODE ){ System.out.println("\tCLOSED:GETHASHED:" + --OPEN_CURSORS); }
            throw ex;
        }
        return answer;

    }

    public static HashMap[] getTableHashedArray( Connection aDB, String sqlQuery)
        throws Exception{ return getTableHashedArray( aDB, sqlQuery, -1 );
    }

    public static HashMap[] getTableHashedArray( Connection wtf, String sqlQuery,
        int max ) throws Exception {

        LinkedList temp = getHashedTable( wtf, sqlQuery, max );

        HashMap[] answer = new HashMap[ temp.size() ];
        for( int i = 0 ; i < temp.size(); i++ ){
            answer[i] = (HashMap) temp.get(i);
        }

        return answer;
    }

    public static Object[][] getTableArray( Connection aDB, String sqlQuery)
        throws Exception{ return getTableArray( aDB, sqlQuery, -1 );}

    public static Object[][] getTableArray( Connection wtf, String sqlQuery,
        int max ) throws Exception {

        //System.out.println("" + sqlQuery );

        LinkedList temp = getTable( wtf, sqlQuery, max );
        int dimX = 0;
        if( temp.size() > 0 ){
            Object[] item = ((Object[]) temp.get(0));
            dimX = item.length;
        }
        Object[][] output = new Object[temp.size()][dimX];

        for( int i = 0; i < temp.size(); i++ ){
            Object[] item = ((Object[]) temp.get(i));
            for( int j = 0; j < dimX; j++ ){
                output[i][j]=item[j];
            }
        }

        return output;

    }


    /**
    * This method returns whether or not it natively supports this
    * SQLtype.
    * @return whether or not I support this type.
    */
    public static boolean isSupported( int type ){

        boolean answer = false;

        //lets face it, im extremely lazy and this is more readable then
        //a really long if statement.
        switch ( type ){
            case Types.BIT:
            case Types.BINARY:
            case Types.BOOLEAN:
            case Types.CHAR:
            case Types.CLOB:
            case Types.DATE:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.VARCHAR:
            case Types.DECIMAL:
            case Types.LONGVARCHAR:
            case Types.NUMERIC:
            case Types.TIME:
            case Types.REAL:
            case Types.BIGINT:
            case Types.TIMESTAMP:
                answer = true;
                break;
            }
            return answer;

        }

    /**
     * This method extracts the object that best represents the data
     * in the database and returns it.
     *
     * @param column - the column of the value to extract from said row.
     * @param type   - what this column was.
     * @return an object that best represents what is in the database.
     */
    private static Object getObject( ResultSet myResultSet, int column,
        int type ) throws Exception{

            Object answer = null;

            // nothing too fancy here, just go down the list of types
            // I support, extract it using the most useful extract and then
            // save it. Note that I'm not using getObject. getobject will
            // correctly cast the object ONCE. Afterwards, you get a
            // "[b@memlocation" object, which is rather useless because
            // you can't cast it into anything useful even if you knew what it
            // was.

            switch( type ){
            //case Types.DATE:
            //    answer = myResultSet.getDate( column );
            //    break;
            case Types.DECIMAL :
            case Types.NUMERIC :
                answer = myResultSet.getBigDecimal( column );
                break;
            case Types.DOUBLE :
            case Types.FLOAT :
                answer = new Double( myResultSet.getDouble(column));
                break;
            case Types.REAL :
                answer = new Float( myResultSet.getFloat(column));
                break;
            case Types.BIGINT :
            case Types.SMALLINT :
            case Types.INTEGER :
                answer = new Integer( myResultSet.getInt(column));
                break;
            case Types.LONGVARCHAR :
            case Types.CLOB :
            case Types.CHAR:
            case Types.VARCHAR :
                answer = myResultSet.getString( column );
                if( answer != null ){ answer = answer.toString().replaceAll("\\s*$",""); }
                break;
            case Types.TIME :
                answer = myResultSet.getTime( column );
                break;
            case Types.DATE:
            case Types.TIMESTAMP :
                answer = myResultSet.getTimestamp( column );
                break;
            case Types.TINYINT :
                answer = new Short( myResultSet.getShort(column));
                break;
            case Types.BIT :
            case Types.BINARY :
            case Types.BOOLEAN :
                answer = new Boolean ( myResultSet.getBoolean( column ) );
                break;
            }
            /*
             *  Some DBMS (Such as ahem access and Filemaker do not return null if it is null.
             *  They instead do something retarded and return like a.... 0. Thus we me adjust and instead spit out
             *  a literal null.
             *
             *  1/12/09 PKN
             */
            if( answer != null && myResultSet.wasNull() ){
                answer = null;
            }
            return answer;
        }

   public static String getSimpleType( int type ) throws Exception{

        String answer = null;

        // nothing too fancy here, just go down the list of types
        // I support, extract it using the most useful extract and then
        // save it. Note that I'm not using getObject. getobject will
        // correctly cast the object ONCE. Afterwards, you get a
        // "[b@memlocation" object, which is rather useless because
        // you can't cast it into anything useful even if you knew what it
        // was.

        switch( type ){
        case Types.DATE:
            answer = "DATE";
            break;
        case Types.DECIMAL :
        case Types.NUMERIC :
            answer = "NUMBER";
            break;
        case Types.DOUBLE :
        case Types.FLOAT :
            answer = "NUMBER";
            break;
        case Types.REAL :
            answer = "NUMBER";
            break;
        case Types.SMALLINT :
        case Types.INTEGER :
            answer = "NUMBER";
            break;
        case Types.LONGVARCHAR :
        case Types.CHAR:
        case Types.VARCHAR :
            answer = "STRING";
            break;
        case Types.TIME :
            answer ="DATE";
            break;
        case Types.TIMESTAMP :
            answer = "DATE";
            break;
        case Types.TINYINT :
            answer = "NUMBER";
            break;
        case Types.BIT :
        case Types.BINARY :
        case Types.BOOLEAN :
            answer = "NUMBER";
            break;
        }
        return answer;
    }
   
       public boolean hasNext() {
        if( lastresult != null && lastresult.isEmpty() ){
            try {
                lastresult = null;
                lastresult = this.getNextHashLine();
            } catch ( Exception ex ){
                ex.printStackTrace();
            }
        }
        if( lastresult == null ){
            this.close();
        }
        return lastresult != null;
    }

    public HashMap next() {
        HashMap temp = lastresult;

        if( temp != null && hasNext() ){
            try {
                lastresult = null;
                lastresult = this.getNextHashLine();
            } catch ( Exception ex ){
                ex.printStackTrace();
            }
        }
        if( temp == null ){
            this.close();
        }
        return temp;
    }
    
    public void remove() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Iterator<HashMap> iterator() {
        return this;
    }
}