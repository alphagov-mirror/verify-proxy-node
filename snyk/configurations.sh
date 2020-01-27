#!/usr/bin/env bash

# This is to allow the snyk_test and snyk_monitor scripts to both use the same list of configurations and stay in sync.
# Any new gradle configurations to be tested should be added here.

# shellcheck disable=SC2034
CONFIGURATIONS="common dropwizard dropwizard_assets opensaml eidas_saml ida_utils verify_saml dev_pki"
