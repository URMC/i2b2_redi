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

import java.io.*;
import java.util.*;

public class ReadWriteTextFile {

  /**
  * Fetch the entire contents of a text file, and return it in a String.
  * This style of implementation does not throw Exceptions to the caller.
  *
  * @param aFile is a file which already exists and can be read.
  */
  static public String getContents(File aFile) {
    //...checks on aFile are elided
    StringBuffer contents = new StringBuffer();

    //declared here only to make visible to finally clause
    BufferedReader input = null;
    try {
      //use buffering
      //this implementation reads one line at a time
      input = new BufferedReader( new FileReader(aFile) );
      String line = null; //not declared within while loop
      while (( line = input.readLine()) != null){
        contents.append(line);
        contents.append(System.getProperty("line.separator"));
      }
    }
    catch (FileNotFoundException ex) {
      //ex.printStackTrace();
    }
    catch (IOException ex){
      //ex.printStackTrace();
    }
    finally {
      try {
        if (input!= null) {
          //flush and close both "input" and its underlying FileReader
          input.close();
        }
      }
      catch (IOException ex) {
        //ex.printStackTrace();
      }
    }
    return contents.toString();
  }

  /**
  * Change the contents of text file in its entirety, overwriting any
  * existing text.
  *
  * This style of implementation throws all exceptions to the caller.
  *
  * @param aFile is an existing file which can be written to.
  * @throws IllegalArgumentException if param does not comply.
  * @throws FileNotFoundException if the file does not exist.
  * @throws IOException if problem encountered during write.
  */
  static public void setContents(File aFile, String aContents)
                                 throws FileNotFoundException, IOException {
    if (aFile == null) {
      throw new IllegalArgumentException("File should not be null.");
    }

    //declared here only to make visible to finally clause; generic reference
    Writer output = null;
    try {
      //use buffering
      output = new BufferedWriter( new FileWriter(aFile) );
      output.write( aContents );
    }
    finally {
      //flush and close both "output" and its underlying FileWriter
      if (output != null) output.close();
    }
  }

  static public void appendContents(File aFile, String aContents)
                                 throws FileNotFoundException, IOException {
    if (aFile == null) {
      throw new IllegalArgumentException("File should not be null.");
    }

    //declared here only to make visible to finally clause; generic reference
    Writer output = null;
    try {
      //use buffering
      output = new BufferedWriter( new FileWriter(aFile,aFile.exists()) );
      output.write( aContents );
    }
    finally {
      //flush and close both "output" and its underlying FileWriter
      if (output != null) output.close();
    }
  }

  public static void main ( String[] aArguments ) throws IOException {
    File testFile = new File("C:\\temp.xls");

    setContents(testFile, "");
    Random random = new Random();
    String stuff = "<html> <table border='1' cellpadding='1' cellspacing='1' >";
    stuff += "<tr>";
    for( char i = 'a'; i <= 'j'; i++ ){ stuff += "<td bgcolor=#666666><b>"+i+"</b></td>"; }
    stuff += "</tr>\n";
    
    System.out.println( "Now Printing to File:" + testFile.getCanonicalPath() );
    System.out.println( "===================================================" );
    for( int i = 0; i < 10; i++ ){
        stuff+="<tr>";
        for( int j = 0; j < 10; j++ ){
            stuff+="<td " + (i % 2 == 0 ? "" : " bgcolor=#CCCCCC") +">"+
                    Integer.toString(Math.abs(random.nextInt()%100))+"</td>";
        }   
        stuff+="</tr>\n";
    }
    stuff += "</html>";
    
    System.out.println( stuff );
    setContents(testFile, stuff);
    System.out.println( "\nNow Trying to Run Excel" );
    System.out.println( "===================================================" );
    Process pcsExplorer = Runtime.getRuntime().exec( "explorer \""+testFile.getCanonicalPath()+"\"" );
  }
} 
