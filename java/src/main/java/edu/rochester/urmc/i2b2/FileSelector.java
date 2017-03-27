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

import java.io.File;
import javax.swing.JFileChooser;

/**
 * This class picks up a GUI selected file. This is because running javax.swing on a headless environment in the 
 * main class yields a HeadlessException even if you do not use the classes.
 * 
 * @author png
 */
public class FileSelector {
    public static File getFile(){
        File answer = null;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        fileChooser.setDialogTitle( "Select profile ini file.");
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            // user selects a file
            answer = fileChooser.getSelectedFile();
        }
        return answer;
    }
    
}
