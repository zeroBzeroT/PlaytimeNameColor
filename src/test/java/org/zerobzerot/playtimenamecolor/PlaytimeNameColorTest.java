package org.zerobzerot.playtimenamecolor;

import junit.framework.TestCase;

import java.util.List;

public class PlaytimeNameColorTest extends TestCase {

    public void testGetMaximumColorIndex() {

        double maxPlaytime = 384 * 2;
        double maxJoinDate = 365 * 2;

        double joinDates[] = {0.001, 2.9, 3, 4.9, 5, 7.9, 8, 15.9, 16, 45.9, 46, 90.9, 91, 182.9, 183, 364.9, 365, 729, 730.1};
        double playTimes[] = {0.00001, 2.9, 3, 5.9, 6, 11.9, 12, 23.9, 24, 47.9, 48, 95.9, 96, 191.9, 192, 383.9, 384, 767, 768.1};

        List<String> colors = PlaytimeNameColor.defaultColors;

        for (int i = 0; i < playTimes.length; i++) {
            double playTime = playTimes[i];
            double joinDate = joinDates[i];


            int indexPlayTime = (int) Math.round(colors.size() - 1 - Math.log(Math.ceil(maxPlaytime / playTime)) / Math.log(2));
            int indexJoinDate = (int) Math.round(colors.size() - 1 - Math.log(Math.ceil(maxJoinDate / joinDate)) / Math.log(2));

            int resultingIndex = Math.max(0, Math.min(colors.size() - 1, Math.min(indexPlayTime, indexJoinDate)));

            System.out.println(playTime + " " + joinDate + " " + indexPlayTime + " " + indexJoinDate + " " + resultingIndex);
        }

        // Not a real test since there is no check if the results are correct
    }
}