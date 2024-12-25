# frozen_string_literal: true

describe Certificate do
  describe '#find' do
    it 'should raise NotImplementedError' do
      expect { Certificate.find }.to raise_error(NotImplementedError)
    end
  end

  describe '#initialize' do
    it 'should raise NoMethodError' do
      expect { Certificate.new }.to raise_error(NoMethodError, /private method/)
    end
  end
end
