# .bashrc

# Source global definitions
if [ -f /etc/bashrc ]; then
	. /etc/bashrc
fi

# Uncomment the following line if you don't like systemctl's auto-paging feature:
# export SYSTEMD_PAGER=

# User specific aliases and functions


# CHECK JAVA MODULES AVAILABLE TO LOAD THEM: module avail openjdk
module load maven/3.5.0 #loads maven
module load openjdk/11.0.2
# module load openjdk/17.0.0_35 #loads java 17… but this has to
