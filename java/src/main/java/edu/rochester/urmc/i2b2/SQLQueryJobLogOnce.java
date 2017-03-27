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
package edu.rochester.urmc.i2b2;

import java.security.MessageDigest;
import java.util.HashSet;

/**
 * The purpose of this class is to extend the log functions so that issues only get logged once.
 * @author png
 */
public class SQLQueryJobLogOnce extends SQLQueryJob {
    
    private HashSet seenalerts = new HashSet();
    
     /**
     * This function ensures that duplicate errors don't show up in the log endlessly.
     * @param data 
     */
    public void log( String data ){
        String md5 = digest( data );
        if( !seenalerts.contains(md5) ){
            super.log(data);
            seenalerts.add(md5);
        }
    }
    
    /**
     * This produces a SHA-1 hash of string.
     * @param String data you want to hash
     * @return SHA-1 hash of the string data
     */
    private String digest( String data ){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(data.getBytes());
            byte[] digest = md.digest();
            StringBuffer sb = new StringBuffer();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch ( Exception ex ){
            ex.printStackTrace();
            return null;
        }
    }
}
