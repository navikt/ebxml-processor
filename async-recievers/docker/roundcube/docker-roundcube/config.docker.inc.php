<?php
  $config['db_dsnw'] = 'sqlite:////var/roundcube/db/sqlite.db?mode=0646';
  $config['db_dsnr'] = '';
  $config['default_host'] = 'greenmail';
  $config['default_port'] = '3143';
  $config['smtp_server'] = 'greenmail';
  $config['smtp_port'] = '3025';
  $config['temp_dir'] = '/tmp/roundcube-temp';
  $config['skin'] = 'elastic';
  $config['plugins'] = array_filter(array_unique(array_merge($config['plugins'], ['archive', 'zipdownload'])));
  
