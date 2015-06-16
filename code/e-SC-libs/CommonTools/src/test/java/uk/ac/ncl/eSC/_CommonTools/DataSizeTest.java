package uk.ac.ncl.eSC._CommonTools;

import static org.junit.Assert.*;

import org.junit.Test;

import uk.ac.ncl.eSC.CommonTools;

public class DataSizeTest
{
    @Test
    public void testToString_1()
    {
        String actual = CommonTools.DataSize.ToString(1000, CommonTools.DataSize.Prefix.SI);
        String expected = "1kB";
        assertEquals(expected, actual);
    }

    @Test
    public void testToString_2()
    {
        String actual = CommonTools.DataSize.ToString(1023, CommonTools.DataSize.Prefix.SI);
        String expected = "1k23B";
        assertEquals(expected, actual);
    }

    @Test
    public void testToString_3()
    {
        String actual = CommonTools.DataSize.ToString(1024, CommonTools.DataSize.Prefix.SI);
        String expected = "1k24B";
        assertEquals(expected, actual);
    }

    @Test
    public void testToString_4()
    {
        String actual = CommonTools.DataSize.ToString(100023, CommonTools.DataSize.Prefix.SI);
        String expected = "100k23B";
        assertEquals(expected, actual);
    }

    @Test
    public void testToString_5()
    {
        String actual = CommonTools.DataSize.ToString(1000001, CommonTools.DataSize.Prefix.SI);
        String expected = "1M1B";
        assertEquals(expected, actual);
    }
    
    @Test
    public void testToString_6()
    {
        String actual = CommonTools.DataSize.ToString(5 * 1024, CommonTools.DataSize.Prefix.JEDEC);
        String expected = "5KB";
        assertEquals(expected, actual);
    }

    @Test
    public void testToString_7()
    {
        String actual = CommonTools.DataSize.ToString(1024 * 1024L, CommonTools.DataSize.Prefix.JEDEC);
        String expected = "1MB";
        assertEquals(expected, actual);
    }

    @Test
    public void testToString_8()
    {
        String actual = CommonTools.DataSize.ToString(1024 * 1024 * 1024L, CommonTools.DataSize.Prefix.JEDEC);
        String expected = "1GB";
        assertEquals(expected, actual);
    }

    @Test
    public void testToString_10()
    {
        String actual = CommonTools.DataSize.ToString(10240L * 1024 * 1024 * 1024L, CommonTools.DataSize.Prefix.JEDEC);
        String expected = "10240GB";
        assertEquals(expected, actual);
    }
}
