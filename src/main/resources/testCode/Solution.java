import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Solution {

    public int twoSum(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
        Class<Solution> solutionClass = Solution.class;
        Solution solution = solutionClass.newInstance();
        Class<?> aClass = ClassUtils.getClass("int");
        Method twoSumMethod = solutionClass.getDeclaredMethod("twoSum", aClass, aClass);
        int result = (int) twoSumMethod.invoke(solution, 10, 20);
        System.out.println(args);
        System.out.println(result);
    }
}