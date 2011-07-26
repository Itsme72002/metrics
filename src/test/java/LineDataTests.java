import junit.framework.Assert;
import java.util.*;

import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;
import org.junit.Test;

import tsdaggregator.LineData;


public class LineDataTests {
	@Test
	public void ConstructTest()	{
		LineData data = new LineData();
		Assert.assertNotNull(data);
	}
	
	@Test
	public void ParseSingleEntry() {
		LineData data = new LineData();
		data.parseLogLine("[initTimestamp=1300976184.02,set/view=26383.8768005,]");
		Map<String, ArrayList<Double>> map = data.getVariables();
		Assert.assertEquals(1, map.size());
                ArrayList setView = map.get("set/view");
                Assert.assertEquals(1, setView.size());
		Assert.assertEquals(26383.8768005, setView.get(0));
		DateTime timestamp = data.getTime();
		Assert.assertEquals(new DateTime((long)(1300976184.02d * 1000d), ISOChronology.getInstanceUTC()), timestamp);
	}
	
	@Test
	public void ParseMultipleEntries() {
		LineData data = new LineData();
		data.parseLogLine("[initTimestamp=1300976164.85,passport/signin=395539.999008,passport/_signinValidatePassword=390913.009644,passport/signinValid=1,]");
		Map<String, ArrayList<Double>> map = data.getVariables();
		Assert.assertEquals(3, map.size());
		Assert.assertEquals(1d, map.get("passport/signinValid").get(0));
		DateTime timestamp = data.getTime();
		Assert.assertEquals(new DateTime((long)(1300976164.85d * 1000d), ISOChronology.getInstanceUTC()), timestamp);
	}
        
        @Test
        public void Parse2aVersionentry() {
            LineData data = new LineData();
            data.parseLogLine("{\"version\":\"2a\",\"counters\":{\"initTimestamp\":1311574570.3563},\"timers\":{\"qmanager\\/index\":[99496.126174927,106616.37284927]},\"annotations\":[]}");
            Map<String, ArrayList<Double>> map = data.getVariables();
            Assert.assertEquals(1, map.size());
            ArrayList<Double> vals = map.get("qmanager/index");
            Assert.assertEquals(99496.126174927d, vals.get(0));
            Assert.assertEquals(106616.37284927d, vals.get(1));
            DateTime timestamp = data.getTime();
            Assert.assertEquals(new DateTime((long)(1311574570.3563d * 1000d), ISOChronology.getInstanceUTC()), timestamp);
        }

}
