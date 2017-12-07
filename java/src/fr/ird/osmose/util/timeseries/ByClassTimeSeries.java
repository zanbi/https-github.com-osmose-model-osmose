/*
 * OSMOSE (Object-oriented Simulator of Marine ecOSystems Exploitation)
 * http://www.osmose-model.org
 * 
 * Copyright IRD (Institut de Recherche pour le Développement) 2013
 * 
 * Contributor(s):
 * Yunne SHIN (yunne.shin@ird.fr),
 * Morgane TRAVERS (morgane.travers@ifremer.fr)
 * Philippe VERLEY (philippe.verley@ird.fr)
 *
 * This software is a computer program whose purpose is to simulate fish
 * populations and their interactions with their biotic and abiotic environment.
 * OSMOSE is a spatial, multispecies and individual-based model which assumes
 * size-based opportunistic predation based on spatio-temporal co-occurrence
 * and size adequacy between a predator and its prey. It represents fish
 * individuals grouped into schools, which are characterized by their size,
 * weight, age, taxonomy and geographical location, and which undergo major
 * processes of fish life cycle (growth, explicit predation, natural and
 * starvation mortalities, reproduction and migration) and fishing mortalities
 * (Shin and Cury 2001, 2004).
 * 
 * This software is governed by the CeCILL-B license under French law and
 * abiding by the rules of distribution of free software.  You can  use, 
 * modify and/ or redistribute the software under the terms of the CeCILL-B
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info". 
 * 
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability. 
 * 
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or 
 * data to be ensured and,  more generally, to use and operate it in the 
 * same conditions as regards security. 
 * 
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-B license and that you accept its terms.
 */
package fr.ird.osmose.util.timeseries;

import au.com.bytecode.opencsv.CSVReader;
import fr.ird.osmose.util.Separator;
import fr.ird.osmose.util.SimulationLinker;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * Reads a CSV file containing time series as a function of age/size class and time.
 *
 * The first line of the file contains the class ages (in years). 
 * The following lines contain the time series for each class in the following format:
 * |time1_class1|time1_class2|time1_class3|time1_class4|time1_class5|
 * |time2_class1|time2_class2|time2_class3|time2_class4|time2_class5|
 * |time3_class1|time3_class2|time3_class3|time3_class4|time3_class5|
 *
 * The number of time steps must be a multiple of {@code nStepYear}.
 *
 * @author pverley
 */
public class ByClassTimeSeries extends SimulationLinker {

    /** Class attribute (size, age, etc.)
     *  
     *  Dimensions: [nclasses]
     */
    private float[] classes;   

    /** Values read from input file
     * Dimensions: [ntimestep][nclasses]
     */
    private double[][] values;  

    public ByClassTimeSeries(int rank) {
        super(rank);
    }

    public void read(String filename) {
        int nStepYear = getConfiguration().getNStepYear();
        int nStepSimu = nStepYear * getConfiguration().getNYear();
        read(filename, nStepYear, nStepSimu);
    }

    public void read(String filename, int nMin, int nMax) {

        int nStepYear = getConfiguration().getNStepYear();
        int nStepSimu = nStepYear * getConfiguration().getNYear();
        try {
            // 1. Open the CSV file
            CSVReader reader = new CSVReader(new FileReader(filename), Separator.guess(filename).getSeparator());
            List<String[]> lines = reader.readAll();

            // 2. Read the threshold values
            String[] lineThreshold = lines.get(0);
            classes = new float[lineThreshold.length - 1];
            for (int k = 0; k < classes.length; k++) {
                classes[k] = Float.valueOf(lineThreshold[k + 1]);
            }

            // 3. Check the length of the time serie and inform the user about potential problems or inconsistencies
            int nTimeSerie = lines.size() - 1;
            if (nTimeSerie < nMin) {
                throw new IOException("Found " + nTimeSerie + " time steps in the time serie. It must contain at least " + nMin + " time steps.");
            }
            if (nTimeSerie % nStepYear != 0) {
                throw new IOException("Found " + nTimeSerie + " time steps in the time serie. It must be a multiple of the number of time steps per year.");
            }
            if (nTimeSerie > nMax) {
                getSimulation().warning("Time serie in file {0} contains {1} steps out of {2}. Osmose will ignore the exceeding years.", new Object[]{filename, nTimeSerie, nMax});
            }
            nTimeSerie = Math.min(nTimeSerie, nMax);

            // 3. Read the mortality rates
            values = new double[nStepSimu][];
            for (int t = 0; t < nTimeSerie; t++) {
                values[t] = new double[lineThreshold.length - 1];
                String[] fval = lines.get(t + 1);
                for (int k = 0; k < values[t].length; k++) {
                    values[t][k] = Double.valueOf(fval[k + 1]);
                }
            }
            // 4. Fill up the time serie if necessary
            if (nTimeSerie < nStepSimu) {
                // There is less season in the file than number of years of the
                // simulation.
                int t = nTimeSerie;
                while (t < nStepSimu) {
                    for (int k = 0; k < nTimeSerie; k++) {
                        values[t] = values[k];
                        t++;
                        if (t == nStepSimu) {
                            break;
                        }
                    }
                }
                getSimulation().warning("Time serie in file {0} only contains {1} steps out of {2}. Osmose will loop over it.", new Object[]{filename, nTimeSerie, nStepSimu});
            }
        } catch (IOException ex) {
            getSimulation().error("Error reading CSV file " + filename, ex);
        }
    }

    public int classOf(float school) {
        // 1. value < first threshold, index does not exist
        if (school < classes[0]) {
            return -1;
        }
        // 2. Normal case thresold[k] <= value < threshold[k+1]
        for (int k = 0; k < classes.length - 1; k++) {
            if ((classes[k] <= school) && (school < classes[k + 1])) {
                return k;
            }
        }
        // 3. value >= threshold[last]
        return classes.length - 1;
    }

    public double getValue(int step, float school) {
        return values[step][classOf(school)];
    }

    public int getNClass() {
        return classes.length;
    }

    public float getClass(int k) {
        return classes[k];
    }

    public float[] getClasses() {
        return classes;
    }

    public double[][] getValues() {
        return values;
    }
}