package uk.ac.ncl.eSC;

public class StringHelper
{
    public static boolean Equals(String s1, String s2)
    {
        if (s1 == s2) return true;
        if (s1 == null || s2 == null) return false;
        
        return s1.equals(s2);
    }
    
    public static boolean IsNullOrEmpty(String s)
    {
        return s == null || "".equals(s);
    }
    
    public static boolean IsNullOrBlank(String s)
    {
        return s == null || "".equals(s.trim());
    }
}
