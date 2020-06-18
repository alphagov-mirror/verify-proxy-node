# Start pages

def country_stub_connector_url(country)
  case country
  when 'Netherlands'
    'https://demo-portal.minez.nl/demoportal/etoegang'
  when 'Estonia'
    # 'https://tara-demo.herokuapp.com/first'
    'https://tara-demo.herokuapp.com/auth?scope=eidas'
  when 'Sweden'
    'https://qa.test.swedenconnect.se/'
  else
    raise ArgumentError.new("Invalid country name: #{country}")
  end
end

# Netherlands

def navigate_netherlands_journey_to_uk
  assert_text('Kies hoe u wilt inloggen')
  click_link('English')
  assert_text('Choose how to log in')
  select "EU Login", :from => "authnServiceId"
  click_button('Continue')
  assert_text('Which country is your ID from?')
  find('#country-GB').click
  click_button('Continue')
end

# Estonia

def navigate_estonia_journey_to_uk
  assert_text("European Union member state's eID")
  find('.selectize-control.js-select-country.single').click
  find('div.option', text: 'United Kingdom').click
  click_button('Continue')
end

def arrive_at_estonia_success_page
  assert_text('Tere, Jack Cornelius Bauer !')
  assert_text('"acr": "substantial"')
  assert_text('date_of_birth": "1984-02-29"')
end

# Sweden

def navigate_sweden_journey_to_uk
  assert_text("Test your eID")
  click_button("Foreign eID")
  click_button("countryFlag_GB")
end

def arrive_at_sweden_success_page
  assert_text("Your eID works for authentication.")
  assert_text("Jack Cornelius")
  assert_text("Bauer")
  assert_text("1984-02-29")
  assert_text("GB")
  assert_text('Your authentication was made according to eIDAS assurance level "Substantial".')
end
