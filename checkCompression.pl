#! /usr/bin/perl 
# Dependency: "sudo cpanp i Math::Random" 

# This program estimates the compression of a list of delta gaps, presuming that they have geometric distribution.
# The gaps are created 8 at at time by truncating exponentially distributed random floats.  
# This is an internal development tool for understanding the distributions, and the output is cryptic. 
#
# The first number "1.60" is the average of the random exponentials.  Once we truncate, the average changes. 
# The top line is shorthand for a diffferent patterns we might try to match.  Not all are actually used.
# 1: Add a high bit (1xxx) to any one element
# 1+1: Add  high bits to any two elements (1xxx + 1xxx)
# 1+1+1: "" to any three elements. (1xxx + 1xxx + 1xxx)
# 2l: Add a 'low two bit' prefix to one element (10xxx)
# 2h: Add a 'high two bit' prefix to one element (11xxx)
# 2l+2l: Add low two's to two elements (10xxx + 10xxx)
# 2h+2h: Same with high two's (11xxx + 11xxx)
# 2l+2h: Add one low two and one high two (10xxx + 11xxx)
# 2l+1:  Add a high bit and a low two to different elements (1xxx + 10xxx)
# 2h+1:  Same with a high two (1xxx + 11xxx)
# 2l+1+1: Add a low two to one element, and high bits to two others (10xxx + 1xxx + 1xxx)
# 2h+1+1: Same with a high two (11xxx + 1xxx + 1xxx)
# These additions can never be to the same element.  
# Note that a pattern can be counted more than once.
#
# The rows represent the width in binary (ceil(log2(x)) of the largest of the 8 elements
# For example, in the chart below, the '17' in row 1 means that 7 zeros and 1 one occured 17 times.
# The '49' in row 3 is the number of times with 7 elements of width 1 or less, and 1 element of width 3
# '166' in row 2 counts 166 occurrences of 2 elements of width two and 6 elements of width 1 or 0.
# The number in parentheses at the end of each row is the number of times that bit width was the max.
# These numbers should add up to the total number of trials.  Number of ints = 8 * numTrials.
#
#   1.60    1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#   0:      0      0      0      0      0      0      0      0      0      0      0 (2)
#   1:      5     17     21      0      0      0      0      0      0      0      0 (54)
#   2:    124    166     92      2      1      0      0      9      7     22     13 (427)
#   3:    349    103     13     49     12     11      2     98     33     84     26 (465)
#   4:     52      0      0     27      0      1      0     15      2      4      1 (52)
#   (3.61) 2.85 2.81 2.53 2.71 3.20 3.22 3.71 3.76 3.76 2.69 (2.53 -1.08)
#
# The bottom line is the avg bits per compressed int for different schemes.   
# The first number in parentheses (3.61) is the BP128 standard.  
# BP128 uses the widest number in a series of 128, plus 1B of overhead (maxBitWidth[128] * 8 + 8)
# The next numbers are on the same row are different compression schemes.   See code for descriptions.
# These schemes are generally in order of optimized size, with the smallest widths on the left.
# As the average gap gets bigger, the most efficient compression scheme moves to the right.
# The last two numbers in parentheses (2.53 -1.08) are the repeated width of the best scheme 
# followed by the difference of this scheme from the standard BP128 in bits/integer.  
#


use strict;
use warnings;
use Math::Random;

sub maxWidth {
    my ($array) = @_;
    my $max = 0;
    for my $element (@$array) {
	# bit width required to store this number
	my $width = $element == 0 ? 0 : int(log($element)/log(2)) + 1;
	if ($width > $max) { $max = $width; }
    }
    return $max;
}

sub numInRange {
    my ($array, $low, $high) = @_;
    my $count = 0;
    for my $element (@$array) {
	if ($element >= $low && $element <= $high) {
	    $count++;
	}
    }
    return $count;
}

# 0.50     1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#  0:      0      0      0      0      0      0      0      0      0      0      0 (3163)
#  1:   3323   1610    447      0      0      0      0      0      0      0      0 (5455)
#  2:   1274     87      1    457     31     67      0    465     46    181     15 (1363)
#  3:     18      1      0     17      1      0      0      1      0      0      0 (19)
# Optimize for 0.50
#  Single bit flag signifies 1-bit data or 8-bit patch (variable additional data)
#  0: 0-bit (8 zeros)
#  1: 0b + 1 (8)
#     0b + 1 + 1 (56)
#     0b + 2l (8)
#     0b + 2l + 1 (56)
#     1b + 1 (8)
#     1b + 1 + 1 (56)
#     0b + 2h (8)
#     1b + 2l (8)    
#     1b + 2h (8)    
#     2b + 1 (8)
#     1b (1)
#     2b (1)
#     3b (1)
#     4b (1)
#     5b (1)
#     6b (1)
#     7b (1)
#     8b (1)
#     extra (24)

sub BP8_1_p0 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { # 0-bit uses just the flag
	return 1;
    }

    if ($max == 1) {
	if ($t0 == 1) { #  0b + 1 (8)
	    return 1 + 8;
	}
	if ($t0 == 2) { #  0b + 1 + 1 (56)
	    return 1 + 8;
	}
    }

    if ($max == 2) {
	if ($t0 == 1 && $t1 == 0) { # 0b + 2l (8),  0b + 2h (8)
	    return 1 + 8;
	}
	if ($t0 == 1 && $t0l == 1 && $t1 == 1) { # 0b + 2l + 1 (56)
	    return 1 + 8;
	}
	if ($t0 == 1) { #  1b + 1 (8)
	    return 1 + 8 + 1*8;
	}
	if ($t0 == 2) { # 1b + 1 + 1 (56)
	    return 1 + 8 + 1*8;
	}
    }

    if ($max == 3) {
	if ($t0 == 1 && $t1 == 0) { # 1b + 2l (8); 1b + 2h (8)    
	    return 1 + 8 + 1*8;
	}
	if ($t0 == 1) { # 2b + 1 (8)
	    return 1 + 8 + 2*8;
	}
    }

    if ($max == 4) {
	if ($t0 == 1 && $t1 == 0) { # 2b + 2l (8), 2b + 2h (8)
	    return 1 + 8 + 2*8;
	}
    }

    if ($max < 8) {
	return 1 + 8 + $max * 8;  # 1b, 2b, 3b, 4b, 5b, 6b, 7b, 8b (8)
    }

    return 1 + 8 + $max * 8; # extra (32)
}

#  1.4      1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#   0:      0      0      0      0      0      0      0      0      0      0      0 (4)
#   1:     12     34     21      0      0      0      0      0      0      0      0 (88)
#   2:    213    193     92     10      6      5      1     32     12     38     33 (532)
#   3:    277     58      7     47     12      9      0     80     15     67     23 (343)
#   4:     33      0      0     25      0      2      0      5      0      1      0 (33)
#
# 2-bit + 8-bit patch: 1, 2, 3, patch
sub BP8_2_p123 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 2 + 1*8;  # built-in 1-bit (no zero)
    }

    if ($max == 1) {
	return 2 + 1*8;  # built-in 1-bit
    }



    if ($max == 2) {
	if ($t0 == 1 && $t1 == 0) { # 0b + 2l,  0b + 2h
	    return 2 + 8;
	}
	if ($t0 == 1 && $t0l == 1 && $t1 == 1) { # 0b + 2l + 1 (56)
	    return 2 + 8;
	}
	return 2 + 2*8; # built-in 2-bit
    }

    if ($max == 3) {
	if ($t0 == 1 && $t1 == 0) { # 1b + 2l; 1b + 2h
	    return 2 + 8 + 1*8;
	}
	if ($t0 == 1 && $t1 == 1) { # 1b + 2l + 1; 1b + 2h + 1    
	    return 2 + 8 + 1*8;
	}
	return 2 + 3*8; # built-in 3-bit
    }

    if ($max == 4) {
	if ($t0 == 1 && $t1 == 0) { # 2b + 2l, 2b + 2h
	    return 2 + 8 + 2*8;
	}
	if ($t0 == 1) { # 3b + 1
	    return 2 + 8 + 3*8;
	}
    }

    if ($max == 5) {
	if ($t0 == 1 && $t1 == 0) { # 3b + 2l, 3b + 2h
	    return 2 + 8 + 3*8;
	}
    }

    if ($max < 8) {
	return 2 + 8 + $max * 8;  # 0b, 4b, 5b, 6b, 8b 

    }

    if ($max < 12) { # extra (3)
	return 2 + 8 + $max * 8;
    }

    return 2 + 8 + 8 + $max * 8;  # extra byte to specify width
}

#4.50       1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#   1:      0      1      0      0      0      0      0      0      0      0      0 (1)
#   2:      3      2      4      0      0      0      0      0      0      0      1 (15)
#   3:     54     58     59      1      2      0      1      5      4      9      3 (203)
#   4:    298    186     58     28      8      7      2     47     27     77     32 (560)
#   5:    196     17      2     40      4     15      0     68     18     31      7 (215)
#   6:      6      0      0      4      0      1      0      1      0      0      0 (6)

# 2-bit + 8-bit patch: 2, 3, 4, patch
sub BP8_2_p234 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 2 + 8;  #  0b
    }

    if ($max == 1) {
	if ($t0 == 1) {
	    return 2 + 8;  # 0b + 1
	}
	return 2 + 8 + 1*8; # 1b
    }

    if ($max == 2) {
	return 2 + 2*8; # built-in 2b
    }

    if ($max == 3) {
	if ($t0 == 1 && $t1 == 0) { # 1b + 2l; 1b + 2h
	    return 2 + 8 + 1*8;
	}
	return 2 + 3*8; # built-in 3-bit
    }

    if ($max == 4) {
	if ($t0 == 1 && $t1 == 0) { # 2b + 2l, 2b + 2h
	    return 2 + 8 + 2*8;
	}
	if ($t0 == 1 && $t0l == 1 && $t1 == 1) { # 2b + 2l + 1
	    return 2 + 8 + 2*8;
	}
	return 2 + 4*8; # built-in 4-bit
    }

    if ($max == 5) {
	if ($t0 == 1 && $t1 == 0) { # 3b + 2l, 3b + 2h
	    return 2 + 8 + 3*8;
	}
	if ($t0 == 1 && $t0l == 1 && $t1 == 1) { # 3b + 2l + 1
	    return 2 + 8 + 3*8;
	}
	if ($t0 == 2) { # 4b + 1 + 1
	    return 2 + 8 + 4*8;
	}
	if ($t0 == 1) { # 4b + 1
	    return 2 + 8 + 4*8;
	}
    }

    if ($max == 6) {
	if ($t0 == 1) { # 5b + 1
	    return 2 + 8 + 5*8;
	}
    }

    if ($max < 8) {
	return 2 + 8 + $max * 8;  # 0b, 1b, 5b, 6b, 8b 

    }

    if ($max < 12) { # extra (3)
	return 2 + 8 + $max * 8;
    }

    return 2 + 8 + 8 + $max * 8;  # extra byte to specify width
}

# 5.00       1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#    2:      2      0      3      0      0      0      0      0      0      0      1 (10)
#    3:     16     35     37      0      0      0      0      1      0      1      2 (123)
#    4:    259    201     60     14      5      3      2     27     18     59     30 (556)
#    5:    267     29      1     63      5     15      0     82     22     54     11 (297)
#    6:     14      0      0     11      0      0      0      2      0      1      0 (14)
# p345
# 2b + 2l + 1
# 2b + 2l
# 2b + 2h
# 3b + 2h + 1
# 3b + 2h
# 3b + 2l + 1
# 3b + 2l
# 4b + 2l
# 5b + 1

# 2-bit + 8-bit patch: 3, 4, 5, patch
sub BP8_2_p345A {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 2 + 8;  #  0b
    }

    if ($max == 1) {
	return 2 + 8 + 1*8; # 1b
    }

    if ($max == 2) {
	return 2 + 8 + 2*8; # 2b
    }

    if ($max == 3) {
	return 2 + 3*8; # built-in 3-bit
    }

    if ($max == 4) {
	if ($t0 == 1 && $t0l == 1 && $t1 == 1) { # 2b + 2l + 1
	    return 2 + 8 + 2*8;
	}
	if ($t0 == 1 && $t1 == 0) { # 2b + 2l, 2b + 2h
	    return 2 + 8 + 2*8;
	}
	return 2 + 4*8; # built-in 4-bit
    }

    if ($max == 5) {
	if ($t0 == 1 && $t1 == 1) { # 3b + 2l + 1, 3b + 2h + 1
	    return 2 + 8 + 3*8;
	}
	if ($t0 == 1 && $t1 == 0) { # 3b + 2l, 3b + 2h
	    return 2 + 8 + 3*8;
	}
	return 2 + 5*8; # built-in 5-bit
    }

    if ($max == 6) {
	if ($t0 == 1 && $t0l == 1 && $t1 == 0) { # 4b + 2l
	    return 2 + 8 + 4*8;
	}
	if ($t0 == 1) { # 5b + 1
	    return 2 + 8 + 5*8;
	}
    }
    if ($max == 7) {
	if ($t0 == 1) { # 6b + 1
	    return 2 + 8 + 6*8;
	}
    }

    return 2 + 8 + $max * 8;
}


# 6.50       1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#    2:      0      0      0      0      0      0      0      0      0      0      0 (1)
#    3:      7     13     16      0      0      0      0      1      1      0      0 (46)
#    4:    158    156     79     10      2      3      1     17     11     34     16 (433)
#    5:    329    119      9     36     14      7      2     89     26     90     21 (458)
#    6:     62      0      0     30      0      1      0     20      3      6      0 (62)
# 2b + 2l
# 2b + 2h
# 3b + 2l + 1
# 3b + 2l
# 3b + 2h + 1
# 3b + 2h
# 4b + 2l + 1
# 4b + 2l
# 5b + 1

# 2-bit + 8-bit patch: 3, 4, 5, patch
sub BP8_2_p345 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 2 + 8;  #  0b
    }

    if ($max == 1) {
	return 2 + 8 + 1*8; # 1b
    }

    if ($max == 2) {
	return 2 + 8 + 2*8; # 2b
    }

    if ($max == 3) {
	return 2 + 3*8; # built-in 3-bit
    }

    if ($max == 4) {
	if ($t0 == 1 && $t1 == 0) { # 2b + 2l, 2b + 2h
	    return 2 + 8 + 2*8;
	}
	return 2 + 4*8; # built-in 4-bit
    }

    if ($max == 5) {
	if ($t0 == 1 && $t1 == 1) { # 3b + 2l + 1, 3b + 2h + 1
	    return 2 + 8 + 3*8;
	}
	if ($t0 == 1 && $t1 == 0) { # 3b + 2l, 3b + 2h
	    return 2 + 8 + 3*8;
	}
	return 2 + 5*8; # built-in 5-bit
    }

    if ($max == 6) {
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 4b + 2l + 1, 4b + 2l
	    return 2 + 8 + 4*8;
	}
	if ($t0 == 1) { # 5b + 1
	    return 2 + 8 + 5*8;
	}
    }
    if ($max == 7) {
	if ($t0 == 1) { # 6b + 1
	    return 2 + 8 + 6*8;
	}
    }

    return 2 + 8 + $max * 8;
}

# 10.00     1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#   3:      0      3      1      0      0      0      1      0      0      0      0 (6)
#   4:     31     59     46      0      1      0      0      2      0      7      7 (166)
#   5:    260    195     76     14     14      4      2     44     15     61     25 (551)
#   6:    232     27      4     63      6      6      0     68     19     49      7 (263)
#   7:     14      0      0     13      0      0      0      1      0      0      0 (14)

sub BP8_2_p456 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 2 + 8;  #  0b
    }

    if ($max == 1) {
	return 2 + 8 + 1*8; # 1b
    }

    if ($max == 2) {
	return 2 + 8 + 2*8; # 2b
    }

    if ($max == 3) {
	return 2 + 8 + 3*8; # 3b
    }

    if ($max == 4) {
	return 2 + 4*8; # built-in 4b
    }

    if ($max == 5) {
	if ($t0 == 1 && $t1 == 0) { # 3b + 2l, 3b + 2h
	    return 2 + 8 + 3*8;
	}
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 3b + 2l + 1, 3b + 2l
	    return 2 + 8 + 3*8;
	}
	if ($t0 == 1 && $t0h == 1 && $t1 == 1) { # 3b + 2h + 1
	    return 2 + 8 + 3*8;
	}
	return 2 + 5*8; # built-in 5b
    }

    if ($max == 6) {
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 4b + 2l + 1, 4b + 2l
	    return 2 + 8 + 4*8;
	}
	if ($t0 == 1 && $t0h == 1 && $t1 == 0) { # 4b + 2h
	    return 2 + 8 + 4*8;
	}
	return 2 + 6*8; # built-in 6b
    }

    if ($max == 7) {
	if ($t0 == 1 && $t1 == 0) { # 5b + 2l, 5b + 2h 
	    return 2 + 8 + 5*8;
	}
	if ($t0 == 1) { # 6b + 1 
	    return 2 + 8 + 6*8;
	}
    }

    if ($max == 8) {
	if ($t0 == 1) { # 7b + 1 
	    return 2 + 8 + 7*8;
	}
    }

    if ($max < 12) { # extra (8 + 3)
	return 2 + 8 + $max * 8;
    }

    return 2 + 8 + 8 + $max * 8;  # extra byte to specify width
}

# 25.00       1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#     4:      0      0      1      0      0      0      0      0      0      0      0 (2)
#     5:     11     18     16      0      0      0      0      0      0      2      0 (80)
#     6:    138    163     94      6      3      4      0     16      4     27     12 (435)
#     7:    314     94     17     44      8     12      2     79     16     72     18 (425)
#     8:     57      1      0     23      1      4      0     25      0      3      1 (58)
# 4b + 2l + 1
# 4b + 2l
# 4b + 2h
# 5b + 2l + 1
# 5b + 2l
# 5b + 2h
# 6b + 2l + 1
# 6b + 2l
# 6b + 2h
# 7b + 1

sub BP8_2_p567 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 2 + 8;  #  0b
    }

    if ($max == 1) {
	return 2 + 8 + 1*8; # 1b
    }

    if ($max == 2) {
	return 2 + 8 + 2*8; # 2b
    }

    if ($max == 3) {
	return 2 + 8 + 3*8; # 3b
    }

    if ($max == 4) {
	return 2 + 8 + 4*8; # 4b
    }

    if ($max == 5) {
	return 2 + 5*8; # built-in 5b
    }

    if ($max == 6) {
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 4b + 2l + 1, 4b + 2l
	    return 2 + 8 + 4*8;
	}
	if ($t0 == 1 && $t0h == 1 && $t1 == 0) { # 4b + 2h
	    return 2 + 8 + 4*8;
	}
	return 2 + 6*8; # built-in 6b
    }

    if ($max == 7) {
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 5b + 2l + 1, 5b + 2l
	    return 2 + 8 + 5*8;
	}
	if ($t0 == 1 && $t0h == 1 && $t1 == 0) { # 5b + 2h 
	    return 2 + 8 + 5*8;
	}
	return 2 + 7*8; # built-in 7b
    }

    if ($max == 8) {
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 6b + 2l + 1, 6b + 2l
	    return 2 + 8 + 6*8;
	}
	if ($t0 == 1 && $t0h == 1 && $t1 == 0) { # 6b + 2h 
	    return 2 + 8 + 6*8;
	}
    }

    return 2 + 8 + $max * 8; # enough free space to specify width without additional
}

#  1.0     1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#  0:      0      0      0      0      0      0      0      0      0      0      0 (24)
#  1:     71    110     80      0      0      0      0      0      0      0      0 (305)
#  2:    350    163     38     33     12      7      0     83     30     79     25 (559)
#  3:    101      9      0     41      1      7      0     31      8      9      3 (110)
#  4:      2      0      0      2      0      0      0      0      0      0      0 (2)
#  Single bit flag signifies 1-bit data or 8-bit patch (variable additional data)
#  0: 1-bit 
#  1: 0b + 2l (8)
#     0b + 2l + 1 (56)
#     0b + 2h (8)
#     0b + 2h + 1 (56)
#     1b + 1 (8)
#     1b + 1 + 1 (56)
#     1b + 2l (8)
#     1b + 2h (8)
#     2b + 1 (8)
#     2b + 2l (8)    
#     2b + 2h (8)    
#     2b (1)
#     3b (1)
#     4b (1)
#     5b (1)
#     6b (1)
#     7b (1)
#     extra (2)


sub BP8_1_p1 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0 || $max == 1) { # both 0 and 1 encoded as 1-bit
	return 1 + 8;
    }

    if ($max == 2) {
	if ($t0 == 1 && $t1 == 0) { # 0b + 2l (8); 0b + 2h (8)
	    return 1 + 8;
	}
	if ($t0 == 1 && $t1 == 1) { # 0b + 2l + 1 (56); 0b + 2h + 1 (56)
	    return 1 + 8;
	}
	if ($t0 == 1 || $t0 == 2) { # 1b + 1 (8) ; 1b + 1 + 1 (56)
	    return 1 + 8 + 1*8;
	}
    }

    if ($max == 3) {
	if ($t0 == 1 && $t1 == 0) { #  1b + 2l (8); 1b + 2h (8)
	    return 1 + 8 + 1*8;
	}
	if ($t0 == 1) { # 2b + 1 (8)
	    return 1 + 8 + 2*8;
	}
    }

    if ($max == 4) {
	if ($t0 == 1 && $t1 == 0) { # 2b + 2l (8), 2b + 2h (8)
	    return 1 + 8 + 2*8;
	}
    }

    if ($max < 8) {
	return 1 + 8 + $max * 8;  # 2b, 3b, 4b, 5b, 6b, 7b (6)
    }

    return 1 + 8 + $max * 8; # use escape in 2b + 1
}

# 100.00    1    1+1  1+1+1     2l  2l+2l     2h  2h+2h   2l+1   2h+1 2l+1+1 2h+1+1
#   6:      0      0      1      0      0      0      0      0      0      0      0 (2)
#   7:      4     21     28      0      1      0      0      0      0      0      1 (72)
#   8:    173    147    103      5      9      7      1     24     11     37     17 (471)
#   9:    328     69     17     40     11     13      0     87     20     71     22 (414)
#  10:     39      2      0     26      1      3      0      8      0      1      1 (41)
#
# 789p
# 6b + 2l + 1
# 6b + 2l
# 6b + 2h
# 7b + 2l + 1
# 7b + 2l
# 7b + 2h + 1
# 7b + 2h
# 8b + 2l
# 8b + 2h
# 9b + 1

sub BP8_2_p789 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 2 + 8;  #  0b
    }

    if ($max == 1) {
	return 2 + 8 + 1*8; # 1b
    }

    if ($max == 2) {
	return 2 + 8 + 2*8; # 2b
    }

    if ($max == 3) {
	return 2 + 8 + 3*8; # 3b
    }

    if ($max == 4) {
	return 2 + 8 + 4*8; # 4b
    }

    if ($max == 5) {
	return 2 + 8 + 5*8; # 5b
    }

    if ($max == 6) {
	return 2 + 8 + 6*8; # 6b
    }

    if ($max == 7) {
	return 2 + 7*8; # built-in 7b
    }

    if ($max == 8) {
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 6b + 2l + 1, 6b + 2l
	    return 2 + 8 + 6*8;
	}
	if ($t0 == 1 && $t0h == 1 && $t1 == 0) { # 6b + 2h
	    return 2 + 8 + 6*8;
	}
	return 2 + 8*8; # built-in 8b
    }


    if ($max == 9) {
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 7b + 2l + 1, 7b + 2l
	    return 2 + 8 + 7*8;
	}
	if ($t0 == 1 && $t0l == 1 && $t1 <= 1) { # 7b + 2h + 1, 7b + 2h
	    return 2 + 8 + 7*8;
	}
	return 2 + 9*8; # built-in 9b
    }

    if ($max == 10) {
	if ($t0 == 1 && $t1 == 0 ) { # 8b + 2l, 8b + 2h
	    return 2 + 8 + 8*8;
	}
	if ($t0 == 1) { # 9b + 1 
	    return 2 + 8 + 9*8;
	}
    }

    return 2 + 8 + $max * 8;  # use extras to specify width
}

sub BP8_8_patch {
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    # save 1 if high one or two elements 1-bit above everything else
    if ($t0 == 1 || $t0 == 2) {
        return 8 + ($max - 1) * 8;
    }

    # save 2 if one high element is 2-bits above everything else
    if (($t0 == 1 && $t0h == 0 && $t1 == 0) ||
        ($t0 == 1 && $t0l == 0 && $t1 == 0)) {
        return 8 + ($max - 2) * 8;
    }

    return 8 + $max * 8;
}

# collects 16 arrays and finds maximum for all
# 8-bit selector assumed (could squeeze to 3?)
my $BP128max = 0;
my $BP128counter = 1;
sub BP128 {
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    $BP128max = $max if ($max > $BP128max);
    return 0 if ($BP128counter++ < 16); # 16 * 8 == 128
    
    my $bits = $BP128max * 128;  
    $bits += 8; # bits of overhead per 128 ints

    $BP128counter = 1;
    $BP128max = 0;
    return $bits;
}

my @BucketNames;
sub matchRandom {
    my ($randomArray, $matchArray) = @_;
    my $max = maxWidth($randomArray);
    @BucketNames = ();  # recreate every time

    my $b = 0;  # bucket number

    $matchArray->[$max][$b]++; $b++;

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx

    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx
    my $t1l = numInRange($randomArray, $m2, $m2 + $m3 - 1);   # 010xxx
    my $t1h = numInRange($randomArray, $m2 + $m3, $m1 - 1);   # 011xxx

    push @BucketNames, "1";
    $matchArray->[$max][$b]++ if ($t0 == 1); $b++; # 1xxx

    push @BucketNames, "1+1";
    $matchArray->[$max][$b]++ if ($t0 == 2); $b++; # 1xxx + 1xxx

    push @BucketNames, "1+1+1";
    $matchArray->[$max][$b++]++ if ($t0 == 3); $b++; # 1xxx + 1xxx + 1xxx

    push @BucketNames, "2l";
    $matchArray->[$max][$b]++ if ($t0 == 1 && $t0h == 0 && $t1 == 0); $b++; # 10xxx

    push @BucketNames, "2l+2l";
    $matchArray->[$max][$b]++ if ($t0 == 2 && $t0h == 0 && $t1 == 0); $b++; # 10xxx + 10xxx

    push @BucketNames, "2h";
    $matchArray->[$max][$b]++ if ($t0 == 1 && $t0l == 0 && $t1 == 0); $b++; # 11xxx

    push @BucketNames, "2h+2h";
    $matchArray->[$max][$b]++ if ($t0 == 2 && $t0l == 0 && $t1 == 0); $b++; # 11xxx + 11xxx
    
    push @BucketNames, "2l+1";
    $matchArray->[$max][$b]++ if ($t0 == 1 && $t0l == 1 && $t1 == 1); $b++; # 10xxx + 1xxx 

    push @BucketNames, "2h+1";
    $matchArray->[$max][$b]++ if ($t0 == 1 && $t0h == 1 && $t1 == 1); $b++; # 11xxx + 1xxx

    push @BucketNames, "2l+1+1";
    $matchArray->[$max][$b]++ if ($t0 == 1 && $t0l == 1 && $t1 == 2); $b++; # 10xxx + 1xxx + 1xxx 

    push @BucketNames, "2h+1+1";
    $matchArray->[$max][$b]++ if ($t0 == 1 && $t0h == 1 && $t1 == 2); $b++; # 11xxx + 1xxx + 1xxx 

    # add 1x to all but 1 or 2
    # $matchArray->[$max][$b]++ if ($t0 == $len - 1);  $b++;  # all but 1
    # $matchArray->[$max][$b]++ if ($t0 == $len - 2);  $b++;  # all but 2

    return $b - 1;  # number of buckets tested
}

sub checkDistribution {
    my ($count, $avg) = @_;

    my @match;
    my @scores;
    my $numBuckets;  # to provide max for loop below
    my $numScorers;
    for (1 .. $count) {
	# get 8 random 0-based delta gaps with a given average value
	my @random8 = map int, Math::Random::random_exponential(8, $avg);
	$numBuckets = matchRandom(\@random8, \@match);
	$numScorers = 0;
	for my $scorer (\&BP128, \&BP8_1_p0, \&BP8_1_p1, \&BP8_2_p123, \&BP8_2_p234, \&BP8_2_p345A, 
			\&BP8_2_p345, \&BP8_2_p456, \&BP8_2_p567, \&BP8_2_p789, \&BP8_8_patch) {
	    $scores[$numScorers++] += &$scorer(\@random8);
	}
    }

    printf("%.2f ", $avg);
    for my $name (@BucketNames) {
	printf("%7s", $name);
    }
    print "\n";

    for my $i (0..32) {
	my $used = $match[$i][0] || 0;
	

	if ($used > 0) {
	    my $bits = sprintf("%2d", $i);
	    print "  $bits: ";
	    for my $j (1..$numBuckets) {
		my $m = $match[$i][$j] || 0;
		$m = sprintf("%6d", $m);
		print "$m ";
	    }
	    print "($used)\n";
	}
    }

    my $bestScore = 32;
    my $standardScore;
    for my $i (0..$numScorers-1) {
	my $totalBits = $scores[$i];
	my $avgBits = $totalBits / ($count * 8);
	$avgBits = sprintf("%.02f", $avgBits);
	if ($i == 0) {
	    $standardScore = $avgBits;
	    print "($avgBits) ";  # first score is the standard
	}
	else {
	    $bestScore = $avgBits if ($avgBits < $bestScore);
	    print "$avgBits "; # save the best of the rest
	}
    }
    my $difference = sprintf("%.2f",  $bestScore - $standardScore);
    print "($bestScore $difference)\n\n";  
}

my $Count = 1000;
for (my $i = 0.1; $i < .95; $i += .1) {
    checkDistribution($Count, sprintf("%.2f", $i));
}
for (my $i = 1; $i < 3.9; $i += .2) {
    checkDistribution($Count, $i);
}
for (my $i = 4; $i < 9.9; $i += .5) {
    checkDistribution($Count, $i);
}
for (my $i = 10; $i < 20; $i += 2) {
    checkDistribution($Count, $i);
}
for (my $i = 20; $i < 100; $i += 5) {
    checkDistribution($Count, $i);
}
for (my $i = 100; $i < 1000; $i += 100) {
    checkDistribution($Count, $i);
}
for (my $i = 1000; $i < 10000; $i += 1000) {
    checkDistribution($Count, $i);
}


####  unused routines


# 1-bit control with escape
sub BP8_1_escape {
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    if ($max == 0) {
	return 1;  # all zeros
    } 

    if ($max == 1) {
	return 1 + 8;
    }

    # escape if control == 1 and 8-bit val is all zeros
    # fall back on another 8-bit selector and then BP8
    return 1 + 8 + 8 + $max * 8; 
}

# simple BP8 with 8 custom choices of width
# we presume that our choices can be ideal
sub BP8_3 {
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    
    my $bits = $max * 8;  
    $bits += 3; # bits of overhead per array
    return $bits;
}

# 2-bit control with escape for wider widths
sub BP8_2_escape {
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    
    my $bits = $max * 8;  

    # two bit marker with an escape that adds a byte
    if ($max <= 3) {
	return $max * 8 + 2;  # 0, 1, 2, 3
    } 

    # wider widths require additional selector byte
    return $max * 8 + 8 + 8;
}


sub BP8_6_patch {
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);  # 1xxxxx

    if ($t0 == 1) {
        return 6 + ($max - 1) * 8;  # save 8 bits if patch matches 
    }

    return 6 + $max * 8;  # else full width
}

# seems to test worse than 2-bit + 8-bit options
# 1-bit + 8-bit patch: patch or 4
sub BP8_1_p4 {  
    my ($randomArray) = @_;
    my $max = maxWidth($randomArray);    

    my $m0 = 2 ** $max;
    my $m1 = $max < 1 ? 0 : 2 ** ($max - 1);
    my $m2 = $max < 2 ? 0 : 2 ** ($max - 2);
    my $m3 = $max < 3 ? 0 : 2 ** ($max - 3);

    my $t0 = numInRange($randomArray, $m1, $m0 - 1);          # 1xxxxx
    my $t0l = numInRange($randomArray, $m1, $m1 + $m2 - 1);   # 10xxxx
    my $t0h = numInRange($randomArray, $m1 + $m2, $m0 - 1);   # 11xxxx
    my $t1 = numInRange($randomArray, $m2, $m1 - 1);          # 01xxxx

    if ($max == 0) { 
	return 1 + 8;  #  0b
    }

    if ($max == 1) {
	return 1 + 8 + 1*8; # 1b
    }

    if ($max == 2) {
	return 1 + 8 + 2*8; # 2b
    }

    if ($max == 3) {
	if ($t0 == 1) {
	    return 1 + 8 + 2*8; # 2b + 1
	}
	return 1 + 8 + 3*8; # 3b
    }

    if ($max == 4) {
	if ($t0 == 1 && $t0l == 1 && $t1 == 1) { # 2b + 2l + 1
	    return 1 + 8 + 2*8;
	}
	if ($t0 == 1 && $t1 == 0) { # 2b + 2l, 2b + 2h
	    return 1 + 8 + 2*8;
	}
	return 1 + 4*8; # built-in 4-bit
    }

    if ($max == 5) {
	if ($t0 == 1 && $t1 == 1) { # 3b + 2l + 1, 3b + 2h + 1
	    return 1 + 8 + 3*8;
	}
	if ($t0 == 1 && $t1 == 0) { # 3b + 2l, 3b + 2h
	    return 1 + 8 + 3*8;
	}
	if ($t0 == 1) { # 4b + 1
	    return 1 + 8 + 4*8;
	}
    }

    if ($max == 6) {
	if ($t0 == 1 && $t0l == 1 && $t1 == 0) { # 4b + 2l
	    return 1 + 8 + 4*8;
	}
	if ($t0 == 1) { # 5b + 1
	    return 1 + 8 + 5*8;
	}
    }
    if ($max == 7) {
	if ($t0 == 1) { # 6b + 1
	    return 1 + 8 + 6*8;
	}
    }

    return 1 + 8 + $max * 8;
}
