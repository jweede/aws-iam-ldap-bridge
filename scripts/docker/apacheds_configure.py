#!/usr/bin/env python3
"""Script for handing ApacheDS config through env vars for docker"""
import os
import logging
import re

import click
import jinja2

HERE = os.path.dirname(os.path.realpath(__file__))
SCRIPT_NAME = os.path.basename(os.path.realpath(__file__))
DEFAULT_TEMPLATE_DIR = os.path.realpath(HERE + "/templates")

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(SCRIPT_NAME)


class ApacheDSConfig(object):

    known_validators = ("iam_password", "iam_secret_key", "iam_dual")
    log4j_levels = ("ALL", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF")

    def __init__(self, template_dir=None, **context):
        self.template_dir = template_dir or DEFAULT_TEMPLATE_DIR

        self.jinja_env = jinja2.Environment(
            undefined=jinja2.StrictUndefined,
            loader=jinja2.FileSystemLoader(self.template_dir),
        )
        self.jinja_env.globals.update(context)
        log.debug("jinja_globals=%r", self.jinja_env.globals)

    def render(self, template, out_file):
        log.info("Rendering %s to %s", template, out_file)
        managed_by = "Generated from {0} by {1}".format(template, SCRIPT_NAME)
        self.jinja_env.get_template(template).stream(managed_by=managed_by).dump(
            out_file
        )


@click.command()
@click.option("--debug", is_flag=True, envvar="DEBUG")
@click.option("--poll-period", default=600)
@click.option("--root-dn", default="dc=iam,dc=aws,dc=org")
@click.option("--external-poller", default="/root/external_ldap_poller.sh")
@click.option(
    "--validator",
    default="iam_dual",
    type=click.Choice(ApacheDSConfig.known_validators),
)
@click.option(
    "--log-level",
    default="INFO",
    type=click.Choice(ApacheDSConfig.log4j_levels),
    help="ApacheDS log level",
)
@click.argument("render_spec", type=click.File("r"))
def configure(debug, render_spec, **context):
    if debug:
        log.setLevel(logging.DEBUG)
    log.debug("context=%r", context)
    obj = ApacheDSConfig(**context)
    for line in render_spec:
        template_name, out_file = re.split(r"\s+", line.strip(), maxsplit=1)
        obj.render(template_name, out_file)


if __name__ == "__main__":
    configure(auto_envvar_prefix="APACHEDS")
