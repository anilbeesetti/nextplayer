# frozen_string_literal: true

require 'tempfile'

describe Keychain do
  describe '#login_keychain' do
    subject { Keychain.login_keychain }

    it 'should be located in the user home directory' do
      expect(subject.filename).to be == File.expand_path('~/Library/Keychains/login.keychain-db')
    end
  end

  describe '#create' do
    let(:password) { 'p4ssw0rd!' }

    it 'should raise NotImplementedError' do
      Tempfile.open('example.keychain-db') do |tmp|
        expect { Keychain.create(tmp.path, password) }.to raise_error(NotImplementedError)
      end
    end
  end

  describe '#list' do
    describe 'when passing no arguments' do
      it 'should list keychains in user domain' do
        expect(Keychain.list).to satisfy { |keychains|
          keychains.map(&:filename) == Keychain.list(:user).map(&:filename)
        }
      end
    end

    describe 'when passing a valid domain' do
      it 'should not raise an error' do
        expect { Keychain.list(:user) }.not_to raise_error
        expect { Keychain.list(:system) }.not_to raise_error
        expect { Keychain.list(:common) }.not_to raise_error
        expect { Keychain.list(:dynamic) }.not_to raise_error
      end
    end

    describe 'when passing an invalid domain' do
      it 'should raise an error' do
        expect { Keychain.list(:invalid) }.to raise_error(NoMethodError) # FIXME
      end
    end
  end
end
