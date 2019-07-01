package com.criteo.publisher;

import com.criteo.publisher.Util.AdUnitType;
import com.criteo.publisher.model.TokenValue;

import java.util.Hashtable;
import java.util.UUID;

public class TokenCache {

    private Hashtable<BidToken, TokenValue> tokenMap;

    public TokenCache() {
        tokenMap = new Hashtable<>();
    }

    public BidToken add(TokenValue tokenValue) {
        BidToken bidToken = new BidToken(UUID.randomUUID());
        tokenMap.put(bidToken, tokenValue);
        return bidToken;
    }

    public TokenValue getTokenValue(BidToken bidToken, AdUnitType adUnitType) {
        if (bidToken == null) {
            return null;
        }

        if (tokenMap.containsKey(bidToken)) {
            TokenValue tokenValue = tokenMap.get(bidToken);
            if (tokenValue.isExpired()) {
                tokenMap.remove(bidToken);
                return null;
            }
            if (tokenValue.getAdUnitType() != adUnitType) {
                return null;
            }
            tokenMap.remove(bidToken);
            return tokenValue;
        } else {
            return null;
        }
    }

}