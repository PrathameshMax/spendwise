package com.prathmesh.spendwise.practice.strings;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TransformSTring {

    public static void main(String[] args) {
        String input = "hello java world";
        String expectedOutput = "#HelloJavaWorld";

       String str =  "#" + Arrays.stream(input.split(" "))
               .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
               .collect(Collectors.joining());
        System.out.println("with stream ===  "+str);



        String str1 =  "#" + Arrays.stream(input.split(" "))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining());
        System.out.println("with core java ===  "+str);
    }
}
