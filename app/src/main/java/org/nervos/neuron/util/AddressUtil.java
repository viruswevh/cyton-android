package org.nervos.neuron.util;

import org.web3j.crypto.Keys;

public class AddressUtil {

    public static boolean isAddressValid(String address) {
        String smallRegex = "^0x[a-f0-9]{40}$";
        String mixRegex = "^0x[a-fA-F0-9]{40}$";
        if (address.matches(smallRegex)) {
            return true;
        } else {
            return address.matches(mixRegex) && Keys.toChecksumAddress(address).equals(address);
        }
    }

}
