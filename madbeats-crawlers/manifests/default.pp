define append_if_no_such_line($file, $line, $refreshonly = 'false') {
   exec { "/bin/echo '$line' >> '$file'":
      unless      => "/bin/grep -Fxqe '$line' '$file'",
      path        => "/bin",
      refreshonly => $refreshonly
   }
}

include '::ntp'

# Update APT Cache
class { 'apt':
  always_apt_update => true,
}

exec { 'apt-get update':
  command => '/usr/bin/apt-get update -qq'
}

file { '/vagrant/elasticsearch':
  ensure => 'directory',
  group  => 'vagrant',
  owner  => 'vagrant',
}

# Java is required
class { 'java': }

# Elasticsearch
class { 'elasticsearch':
  java_install => true,
  manage_repo  => true,
  repo_version => '1.4',
}

elasticsearch::instance { 'madbeats-01':
  config => {
    'network.host' => '0.0.0.0',
    'node.name' => 'Bomber Man'
  },
}

elasticsearch::plugin{'lmenezes/elasticsearch-kopf':
  module_dir => 'kopf',
  instances  => 'madbeats-01'
}
