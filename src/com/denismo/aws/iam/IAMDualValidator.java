package com.denismo.aws.iam;

import com.denismo.apacheds.auth.AWSIAMAuthenticator;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapAuthenticationException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Jon Wedaman (jweede) on 3/29/16.
 *
 * Tries all IAM validation methods.
 */
public class IAMDualValidator implements _IAMPasswordValidator {

    private List<_IAMPasswordValidator> otherValidators = new LinkedList<>();

    public IAMDualValidator() {
        for (AWSIAMAuthenticator.IamValidator value : AWSIAMAuthenticator.IamValidator.values()) {
            if ( value != AWSIAMAuthenticator.IamValidator.DUAL_VALIDATOR)
                this.otherValidators.add(value.createValidator());
        }
    }

    @Override
    public boolean verifyIAMPassword(Entry user, String pw) throws LdapInvalidAttributeValueException, LdapAuthenticationException {
        for (_IAMPasswordValidator validator : this.otherValidators) {
            if ( validator.verifyIAMPassword(user, pw) ) return true;
        }
        return false;
    }
}
